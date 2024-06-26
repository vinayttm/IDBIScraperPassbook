package com.example.IDBIScraperPassbook.Services;

import static com.example.IDBIScraperPassbook.Utils.AccessibilityUtil.*;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.example.IDBIScraperPassbook.MainActivity;
import com.example.IDBIScraperPassbook.Repository.CheckUpiStatus;
import com.example.IDBIScraperPassbook.Repository.QueryUPIStatus;
import com.example.IDBIScraperPassbook.Repository.SaveBankTransaction;
import com.example.IDBIScraperPassbook.Repository.UpdateDateForScrapper;
import com.example.IDBIScraperPassbook.Utils.AES;
import com.example.IDBIScraperPassbook.Utils.CaptureTicker;
import com.example.IDBIScraperPassbook.Utils.Config;
import com.example.IDBIScraperPassbook.Utils.DeviceInfo;
import com.example.IDBIScraperPassbook.Utils.SharedData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IDBIPassbookRecorderService extends AccessibilityService {
    boolean loginOnce = true;
    boolean isNavPassbook = false;
    boolean isHome = false;
    int appNotOpenCounter = 0;
    int scrollCount = 0;
    int allTransactionIndex = -1;
    int totalBalanceIndex = -1;
    Handler logoutHandler = new Handler();
    final CaptureTicker ticker = new CaptureTicker(this::processTickerEvent);
    CheckUpiStatus upiStatusChecker = new CheckUpiStatus();

    @Override
    protected void onServiceConnected() {
        ticker.startChecking();
        super.onServiceConnected();
    }

    boolean shouldLogout = false;
    private final Runnable logoutRunnable = () -> {
        Log.d("Logout Handler", "Finished");
        shouldLogout = true;
    };

    private void processTickerEvent() {
        Log.d("Ticker", "Processing Event");
        Log.d("Flags", printAllFlags());
        ticker.setNotIdle();
        if (!MainActivity.isAccessibilityServiceEnabled(this, this.getClass())) {
            return;
        }

        AccessibilityNodeInfo rootNode = getTopMostParentNode(getRootInActiveWindow());
        if (rootNode != null) {
            if (findNodeByPackageName(rootNode, Config.packageName) == null) {
                if (appNotOpenCounter > 4) {
                    isNavPassbook = false;
                    isHome = false;
                    allTransactionIndex = -1;
                    totalBalanceIndex = -1;
                    scrollCount = 0;
                    Log.d("App Status", "Not Found");
                    relaunchApp();
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    appNotOpenCounter = 0;
                    return;
                }
                logoutHandler.removeCallbacks(logoutRunnable);
                logoutHandler.postDelayed(logoutRunnable, 1000 * 60 * 5);
                appNotOpenCounter++;
            } else {
                if (shouldLogout) {
                    exitApp();
                } else {
                    Log.d("App Status", "Found");
                    checkForSessionExpiry();
                    listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
                    initialEvent();
                    enterPin();
                    if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("All Transactions")) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        readTransactions();
                    }
                    passBookNav();
                }


//                checkForSessionExpiry();
//                listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
//                CheckUpiStatus.loginCallBack callback = new CheckUpiStatus.loginCallBack() {
//                    @Override
//                    public void onResult(boolean isSuccess) {
//                        if (isSuccess) {
//                            initialEvent();
//                            enterPin();
//
//                            if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("All Transactions")) {
//                                try {
//                                    Thread.sleep(5000);
//                                } catch (InterruptedException e) {
//                                    throw new RuntimeException(e);
//                                }
//                                readTransactions();
//                            }
//                            passBookNav();
//                            if (shouldLogout) {
//                                exitApp();
//                            }
//                        } else {
//                            exitApp();
//                            closeApp();
//                        }
//                    }
//                };
//                upiStatusChecker.checkUpiStatus(callback);


            }
            rootNode.recycle();
        }
    }

    private void closeApp() {
        if (!listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Enter MPIN here")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            isNavPassbook = false;
            isHome = false;
            allTransactionIndex = -1;
            totalBalanceIndex = -1;
            scrollCount = 0;
        }
    }


    private void initialEvent() {
        List<String> extractedData = listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
        if (!extractedData.contains("All Transactions")) {
            AccessibilityNodeInfo mPassbook = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "mPassbook", true, false);
            if (mPassbook != null) {
                Rect outBounds = new Rect();
                mPassbook.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY());
                mPassbook.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    }

    private void relaunchApp() {
        if (MainActivity.isAccessibilityServiceEnabled(this, this.getClass())) {
            new QueryUPIStatus(() -> {
                Intent intent = getPackageManager().getLaunchIntentForPackage(Config.packageName);
                startActivity(intent);
            }, () -> {
                Toast.makeText(this, "Scrapper inactive", Toast.LENGTH_SHORT).show();
            }).evaluate();
        }
    }


    public void enterPin() {
        String pinValue = Config.loginPin;
        if (!pinValue.isEmpty()) {
            if (listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow())).contains("Welcome")) {
                AccessibilityNodeInfo editText = findNodeByClassName(getTopMostParentNode(getRootInActiveWindow()), "android.widget.EditText");
                if (editText != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pinValue.trim());
                    editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                }

                AccessibilityNodeInfo login = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "LOGIN", false, true);
                if (login != null) {
                    Rect outBounds = new Rect();
                    login.getBoundsInScreen(outBounds);
                    performTap(outBounds.centerX(), outBounds.centerY());
                    login.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
    }

    private void exitApp() {
        AccessibilityNodeInfo exitNode = findNodeByContentDescription(getTopMostParentNode(getRootInActiveWindow()), "Exit");
        if (exitNode != null) {
            exitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            shouldLogout = false;
            logoutHandler.removeCallbacks(logoutRunnable);
            logoutHandler.postDelayed(logoutRunnable, 1000 * 60 * 5);
            ticker.setNotIdle();
        }
    }


    private void passBookNav() {
        if (isNavPassbook) return;
        AccessibilityNodeInfo navPassbook = findNodeByContentDescription(getTopMostParentNode(getRootInActiveWindow()), "Passbook");
        if (navPassbook != null) {
            boolean isClick = navPassbook.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (isClick) {
                isNavPassbook = true;
            }
        }
    }


    private void homeNav() {
        if (isHome) return;
        AccessibilityNodeInfo navPassbook = findNodeByContentDescription(getTopMostParentNode(getRootInActiveWindow()), "Home");
        if (navPassbook != null) {
            boolean isClick = navPassbook.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (isClick) {
                isHome = true;
                isNavPassbook = false;
            }
        }
    }


    private String printAllFlags() {
        StringBuilder result = new StringBuilder();
        Field[] fields = getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            try {
                Object value = field.get(this);
                result.append(fieldName).append(": ").append(value).append("\n");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    public boolean performTap(int x, int y) {
        Log.d("Accessibility", "Tapping " + x + " and " + y);
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(p, 0, 950));

        GestureDescription gestureDescription = gestureBuilder.build();

        boolean dispatchResult = false;
        dispatchResult = dispatchGesture(gestureDescription, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
        Log.d("Dispatch Result", String.valueOf(dispatchResult));
        return dispatchResult;
    }

    public boolean performTap(int x, int y, int duration) {
        Log.d("Accessibility", "Tapping " + x + " and " + y);
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(p, 0, duration));

        GestureDescription gestureDescription = gestureBuilder.build();

        boolean dispatchResult = false;
        dispatchResult = dispatchGesture(gestureDescription, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }
        }, null);
        Log.d("Dispatch Result", String.valueOf(dispatchResult));
        return dispatchResult;
    }


    private String getUPIId(String description) {
        try {
            if (!description.contains("@")) return "";
            String[] split = description.split("/");
            String value = null;
            value = Arrays.stream(split).filter(x -> x.contains("@")).findFirst().orElse(null);
            return value != null ? value : "";
        } catch (Exception ex) {
            Log.d("Exception", ex.getMessage());
            return "";
        }
    }

    private String extractUTRFromDesc(String description) {
        try {
            String[] split = description.split("/");
            String value = null;
            value = Arrays.stream(split).filter(x -> x.length() == 12).findFirst().orElse(null);
            if (value != null) {
                return value + " " + description;
            }
            return description;
        } catch (Exception e) {
            return description;
        }
    }


    @Override
    public void onInterrupt() {

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }


    public static String convertDateFormat(String inputDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MMM-yy", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("d/MM/yyyy", Locale.US);
            Date date = inputFormat.parse(inputDate);
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }


    public void readTransactions() {
        ticker.setNotIdle();
        JSONArray output = new JSONArray();
        String balance = "";
        String modelNumber = "";
        String secureId = "";
        List<String> extractedData = listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
        extractedData.removeIf(String::isEmpty);
        List<String> unfilterList = new ArrayList<>();
        for (int i = 0; i < extractedData.size(); i++) {
            if (extractedData.get(i).contains("All Transactions")) {
                allTransactionIndex = i;
                totalBalanceIndex = i - 1;
                unfilterList = extractedData.subList(allTransactionIndex, extractedData.size());
            }
        }
        if (unfilterList.contains("All Transactions")) {
            unfilterList.remove(0);
            unfilterList.subList(unfilterList.size() - 5, unfilterList.size()).clear();
            balance = extractedData.get(totalBalanceIndex);
            balance = balance.replace("₹", "").trim();
        }
        if (DeviceInfo.getModelNumber() != null && Config.context != null) {
            modelNumber = DeviceInfo.getModelNumber();
            secureId = DeviceInfo.generateSecureId(Config.context);
        }
        List<String> dataList = new ArrayList<>();
        for (String str : unfilterList) {
            if (!str.trim().isEmpty()) {
                dataList.add(str);
            }
        }
        List<String> findUntagged = listAllTextsInActiveWindow(getTopMostParentNode(getRootInActiveWindow()));
        if (findUntagged.contains("Untagged")) {
            System.out.println("dataList " + dataList);
            for (int i = 0; i < dataList.size(); i += 4) {
                JSONObject jsonObject = new JSONObject();
                String description = dataList.get(i);
                String date = dataList.get(i + 1);
                String amount = dataList.get(i + 3);
                if (amount.contains("Dr")) {
                    amount = "-" + amount;
                }
                if (amount.contains("₹")) {
                    amount = amount.replace("₹", "").trim();
                    amount = amount.replace("Cr", "").trim();
                    amount = amount.replace("Dr", "").trim();
                }
                try {
                    jsonObject.put("Description", extractUTRFromDesc(description));
                    jsonObject.put("UPIId", getUPIId(description));
                    jsonObject.put("CreatedDate", convertDateFormat(date));
                    jsonObject.put("Amount", amount.replace(" ", ""));
                    jsonObject.put("RefNumber", extractUTRFromDesc(description));
                    jsonObject.put("AccountBalance", balance);
                    jsonObject.put("BankName", Config.bankName + Config.bankLoginId);
                    jsonObject.put("BankLoginId", Config.bankLoginId);
                    jsonObject.put("DeviceInfo", modelNumber + "-" + secureId);

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                output.put(jsonObject);

            }
            if (output.length() > 0) {
                AccessibilityNodeInfo scrollNode = findNodeByClassName(getTopMostParentNode(getRootInActiveWindow()), "android.widget.ListView");
                if (scrollNode != null) {
                    while (scrollCount < 4) {
                        Rect scrollBounds = new Rect();
                        scrollNode.getBoundsInScreen(scrollBounds);
                        int startX = scrollBounds.centerX();
                        int startY = scrollBounds.centerY();
                        int endX = startX;
                        int scrollDistance = 150;
                        int endY = startY - scrollDistance;
                        Path path = new Path();
                        path.moveTo(startX, startY);
                        path.lineTo(endX, endY);
                        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
                        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 500));
                        dispatchGesture(gestureBuilder.build(), null, null);
                        Log.d("API BODY", output.toString());
                        Log.d("API BODY Length", String.valueOf(output.length()));
                        JSONObject result = new JSONObject();
                        try {
                            result.put("Result", AES.encrypt(output.toString()));
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        new QueryUPIStatus(() -> {
                            new SaveBankTransaction(() -> {
                            }, () -> {
                            }).evaluate(result.toString());
                            new UpdateDateForScrapper().evaluate();
                        }, () -> {
                        }).evaluate();
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        scrollCount++;
                    }
                    if (scrollCount == 4) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        isNavPassbook = false;
                        isHome = false;
                        scrollCount = 0;
                        allTransactionIndex = -1;
                        totalBalanceIndex = -1;
                    }
                }
            }
        } else {
            isNavPassbook = false;
        }
    }


    public void checkForSessionExpiry() {
        ticker.setNotIdle();
        AccessibilityNodeInfo targetNode1 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "I Accept the Risk and provide my consent to proceed", false, false);
//        AccessibilityNodeInfo targetNode2 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Sorry! We are unable to service your request at this time. Please try again.", false, false);
        AccessibilityNodeInfo targetNode3 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Session Expired. Please login again.", false, false);
//        AccessibilityNodeInfo targetNode4 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "error:1e00007b:Cipher functions:OPENSSL_internal:WRONG_FINAL_BLOCK_LENGTH", false, false);
        AccessibilityNodeInfo targetNode5 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Alert!", false, false);
        AccessibilityNodeInfo targetNode6 = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Do you want to logout?", false, false);


        if (targetNode1 != null) {
            targetNode1.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            isNavPassbook = false;
            targetNode1.recycle();
            ticker.setNotIdle();
        }

//        if (targetNode2 != null || targetNode3 != null) {
//            AccessibilityNodeInfo okBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()),
//                    "OK", true, false);
//            if (okBtn != null) {
//                Rect outBounds = new Rect();
//                okBtn.getBoundsInScreen(outBounds);
//                performTap(outBounds.centerX(), outBounds.centerY());
//                okBtn.recycle();
//                ticker.setNotIdle();
//            }
//        }
//        if (targetNode5 != null || targetNode4 != null) {
//            AccessibilityNodeInfo okBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()),
//                    "OK", true, false);
//            if (okBtn != null) {
//                okBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                isNavPassbook = false;
//                ticker.setNotIdle();
//            }
//
//        }

        if (targetNode3 != null) {
            AccessibilityNodeInfo okBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()),
                    "OK", true, false);
            if (okBtn != null) {
                isNavPassbook = false;
                Rect outBounds = new Rect();
                okBtn.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY());
                okBtn.recycle();
                ticker.setNotIdle();
            }
        }

        if (targetNode5 != null) {
            AccessibilityNodeInfo okBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()),
                    "OK", true, false);
            if (okBtn != null) {
                isNavPassbook = false;
                Rect outBounds = new Rect();
                okBtn.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY());
                okBtn.recycle();
                ticker.setNotIdle();
            }
        }
        if (targetNode6 != null) {
            AccessibilityNodeInfo yesBtn = findNodeByText(getTopMostParentNode(getRootInActiveWindow()),
                    "YES", false, false);
            if (yesBtn != null) {
                Rect outBounds = new Rect();
                yesBtn.getBoundsInScreen(outBounds);
                performTap(outBounds.centerX(), outBounds.centerY());
                yesBtn.recycle();
                isNavPassbook = false;
                isHome = false;
                totalBalanceIndex = -1;
                allTransactionIndex = -1;
                ticker.setNotIdle();
            }
        }

//        AccessibilityNodeInfo msg = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Are you sure want to exit?", false, false);
//        if (msg != null) {
//            AccessibilityNodeInfo confirm = findNodeByText(getTopMostParentNode(getRootInActiveWindow()), "Confirm", true, true);
//            if (confirm != null) {
//                confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//                ticker.setNotIdle();
//            }
//        }
    }
}