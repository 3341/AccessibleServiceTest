package com.byq.acceservice;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.GsonUtils;
import com.byq.applib.broadcast.CommunicateBroadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import es.dmoral.toasty.Toasty;

/**
 * @功能:
 * @author admin
 * 实现屏幕内容的获取
 */
public class MainService extends AccessibilityService {
    public static final String CALL_RESPONSE_TAG = "tryCallService";
    public static final String UPDATE_CONFIG_TAG = "updateConfig";
    public static final String EXPORT_ANSWER_TO_FILE = "exportAnswerToFile";
    private static final String TAG = BuildConfig.APPLICATION_ID;

    private DaoSession daoSession;
    private QuestionDBDao questionDBDao;

    private void initGreenDao() {
        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, "aserbao.db");
        SQLiteDatabase db = helper.getWritableDatabase();
        DaoMaster daoMaster = new DaoMaster(db);
        daoSession = daoMaster.newSession();
        questionDBDao = daoSession.getQuestionDBDao();
    }


    private List<QuestionGson> questionGsons = new ArrayList<>();
    private ArrayList<AccessibilityNodeInfo> operationNode = new ArrayList<>();
    private String lastQuestion = "";
    private boolean isWaitCurrentAnswer;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
        //配置监听的事件类型为界面变化|点击事件
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_CLICKED;
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        if (Build.VERSION.SDK_INT >= 16) {
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        }
        setServiceInfo(config);
        init();
        initGreenDao();
        Log.i(TAG, "onServiceConnected: initialize db succeed");
    }

    public static String readStreamContent(InputStream inputStream) throws IOException {
        Scanner scanner = new Scanner(inputStream);
        StringBuilder sb = new StringBuilder();
        while(scanner.hasNextLine()) {
            sb.append(scanner.nextLine()+"\n");
        }

        if (sb.length() != 0) sb.deleteCharAt(sb.length()-1);
        inputStream.close();
        return sb.toString();
    }

    /**
     * 初始化答案
     */
    private void initAnswer() {
        Log.i(TAG, "initAnswer: loading answer....");
        try {
            InputStream open = getAssets().open("answer.json");
            String s = readStreamContent(open);
            JSONArray array = new JSONArray(s);
            for (int i = 0; i < array.length(); i++) {
                JSONObject jsonObject = array.getJSONObject(i);
                questionGsons.add(GsonUtils.fromJson(jsonObject.toString(),QuestionGson.class));
            }
        } catch (Exception e) {
            Log.e(TAG, "initAnswer: ", e);
            e.printStackTrace();
        }
        Log.i(TAG, "initAnswer: loading answer done.");
    }

    private class QuestionAnswerFilter extends NodeFilter  {

        private int qNum = -1;
        private String question;
        private String[] answers = new String[4];
        private String currentAnswer;
        private int currentIndex;
        private AccessibilityNodeInfo[] answerNodes = new AccessibilityNodeInfo[4];
        private AccessibilityNodeInfo commitNode;
        private AccessibilityNodeInfo nextQNode;
        private AccessibilityNodeInfo lastQNode;

        public QuestionAnswerFilter() {
            super(MainService.this);
        }

        @Override
        public String toString() {
            return "QuestionAnswerFilter{" +
                    "qNum=" + qNum +
                    ", question='" + question + '\'' +
                    ", answers=" + Arrays.toString(answers) +
                    ", currentAnswer='" + currentAnswer + '\'' +
                    ", currentIndex=" + currentIndex +
                    '}';
        }

        @Override
        public boolean filterNode(AccessibilityNodeInfo nodeInfo) {
            String text = nodeInfo.getText() == null ? null : nodeInfo.getText().toString();
            String[] se = {"A","B","C","D"};
            if (text != null) {
//                Log.i(TAG, "filterNode: "+text);
                Pattern compile = Pattern.compile("\\d+、");
                Matcher matcher = compile.matcher(text);
                Matcher matcherSelection = Pattern.compile("[ABCD](?=\\.)").matcher(text);

                if (text.equals("下一题")) {
                    nextQNode = nodeInfo;
                }

                if (text.equals("上一题")) {
                    lastQNode = nodeInfo;
                }

                if (text.equals("提交")) {
                    commitNode = nodeInfo;
                }

                if (matcher.find()) {
                    question = text;
                    String group = matcher.group();
                    group = group.replaceAll("、", "");
                    try {
                        qNum = Integer.parseInt(group);
                    } catch (NumberFormatException e) {
                        Toasty.error(MainService.this, "错误：无法识别题号").show();
                        e.printStackTrace();
                    }
                } else if (text.contains("正确答案")) {
                    text = text.replaceAll("正确答案：","");
                    currentAnswer = text;
                    for (int i = 0; i < se.length; i++) {
                        if (se[i].equals(text)) {
                            currentIndex = i;
                        }
                    }
                } else if (matcherSelection.find()) {
                    for (int i = 0; i < se.length; i++) {
                        if (se[i].equals(matcherSelection.group())) {
                            answers[i] = text;
                            answerNodes[i] = nodeInfo;
                        }
                    }
                }
            }
            return false;
        }
    }

    /**
     * 搜索节点实现
     * @param nodeInfo
     * @param nodeFilter
     * @return
     */
    private boolean searchNode(AccessibilityNodeInfo nodeInfo,NodeFilter nodeFilter) {
        if (nodeInfo == null) return false;
//        if (nodeInfo.getPackageName().equals("com.yiban.app")) Log.i(TAG, "searchNode: "+nodeInfo);
        boolean result = nodeFilter.filterNode(nodeInfo);
        if (result) return true;

        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = nodeInfo.getChild(i);
            boolean b = searchNode(child,nodeFilter);
            if (b) {
                return true;
            }
        }
        return false;
    }

    private void clickNode(AccessibilityNodeInfo nodeInfo,int delay) {
//        int i = 0;
//        if (i == 0) return;
        Log.i(TAG, "clickNode: try click node "+ getNodeString(nodeInfo));
        Rect rect = new Rect();
        nodeInfo.getBoundsInScreen(rect);
        Path path = new Path();
        path.moveTo(rect.centerX(),rect.centerY());
        GestureDescription.StrokeDescription strokeDescription = new GestureDescription.StrokeDescription(path, delay, 10);
        GestureDescription.Builder builder = new GestureDescription.Builder();

        builder.addStroke(strokeDescription);
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
            }
        }, new Handler());
    }

    private String getNodeString(AccessibilityNodeInfo nodeInfo) {
        return nodeInfo.getText() == null ? "" : nodeInfo.getText().toString();
    }

    /**
     * 将题目过滤题号
     * @return
     */
    private String filterQuestion(String question, int qn) {
        String rep = qn+"、";
        return question.replace(rep,"");
    }

    public static abstract class NodeFilter {
        private MainService mainService;

        public NodeFilter(MainService mainService) {
            this.mainService = mainService;
        }

        private MainService getService() {
            return mainService;
        }

        /**
         * 筛选node
         * @param nodeInfo
         * @return 是否停止搜索 ： 指已经找到node
         */
        public abstract boolean filterNode(AccessibilityNodeInfo nodeInfo);
    }

    private HashMap<String,QuestionGson> questionGsonHashMap = new HashMap<>();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo nodeInfo = event.getSource();//当前界面的可访问节点信息

//        Log.i("test", "onAccessibilityEvent: is node null : " + (nodeInfo == null));
        if (nodeInfo != null) {
            if(nodeInfo.getPackageName().equals("com.yiban.app")) {
                Log.i(TAG, "onAccessibilityEvent: check operation");
                for (int i = 0; i < operationNode.size(); i++) {
                    Log.i(TAG, "onAccessibilityEvent: "+operationNode);
                    if (operationNode.get(i) != null) {
                        Log.i(TAG, "onAccessibilityEvent: click " + getNodeString(operationNode.get(i)));
                        AccessibilityNodeInfo nodeInfo1 = operationNode.get(i);
                        operationNode.remove(i);
                        clickNode(nodeInfo1,0);
                        return;
                    }
                }

                nodeInfo = getRootInActiveWindow(); //获取全部节点
                Log.e(TAG, "onAccessibilityEvent: searched target app" );
                QuestionAnswerFilter nodeFilter = new QuestionAnswerFilter();
                searchNode(nodeInfo, nodeFilter);
                Log.i(TAG, "onAccessibilityEvent: "+nodeFilter);
                if (nodeFilter.question == null) {
                    Toasty.error(MainService.this,"题目无法识别").show();
                } else {
                    List<QuestionDB> questionDBList = questionDBDao.queryBuilder()
                            .where(QuestionDBDao.Properties.Question.eq(filterQuestion(nodeFilter.question, nodeFilter.qNum))).list();


                    if (isWaitCurrentAnswer) {
                        String currentAnswer = nodeFilter.currentAnswer;
                        if (currentAnswer == null) Log.e(TAG, "onAccessibilityEvent: error, answer not found" );
                        else {
                            QuestionDB qb = new QuestionDB();
                            qb.setCurrentAnswer(nodeFilter.currentAnswer);
                            qb.setCurrentIndex(nodeFilter.currentIndex);
                            qb.setQNum(nodeFilter.qNum);
                            qb.setQuestion(filterQuestion(nodeFilter.question,nodeFilter.qNum));
                            questionDBDao.insert(qb);
                            Log.i(TAG, "onAccessibilityEvent: insert");
                        }
                        isWaitCurrentAnswer = false;
                        AccessibilityNodeInfo nextQNode = nodeFilter.nextQNode;
                        clickNode(nextQNode,0);
                    }

                    if (questionDBList.size() < 0) {
                        Toasty.error(MainService.this, "错误：答案居然有两个，请自行判断").show();
                        Log.e(TAG, "onAccessibilityEvent: error:two answer." );
                    } else if (!questionDBList.isEmpty() ){
                        QuestionDB qdb = questionDBList.get(0);
                        Log.i(TAG, "onAccessibilityEvent: findAnswer "+qdb.getCurrentAnswer());
                        boolean isSign = qdb.getCurrentAnswer().length() == 1;
                        if (isSign) {
                            AccessibilityNodeInfo answerNode = nodeFilter.answerNodes[qdb.currentIndex];
                            if (!lastQuestion.equals(nodeFilter.question)) {
                                clickNode(answerNode, 0);
                                operationNode.add(nodeFilter.commitNode);
                                operationNode.add(nodeFilter.nextQNode);
                                lastQuestion = nodeFilter.question;
                            } else {
                                Log.e(TAG, "onAccessibilityEvent: repeat" );
                            }
                        } else {
                            String[] split = qdb.getCurrentAnswer().split("、");
                            String[] se = {"A","B","C","D"};
                            for (String sp : split) {
                                for (int i = 0; i < se.length; i++) {
                                    if (sp.equals(se[i]))
                                        operationNode.add(nodeFilter.answerNodes[i]);
                                }
                            }
                            operationNode.add(nodeFilter.commitNode);
                            operationNode.add(nodeFilter.nextQNode);
                            AccessibilityNodeInfo accessibilityNodeInfo = operationNode.get(0);
                            operationNode.remove(0);
                            clickNode(accessibilityNodeInfo,0);
                        }
                    } else {
                        Toasty.error(MainService.this,"错误：找不到答案，请自行判断").show();
                        Log.e(TAG, "onAccessibilityEvent: not found answer." );
                        isWaitCurrentAnswer = true;
                        AccessibilityNodeInfo answerNode = nodeFilter.answerNodes[0];
                        clickNode(answerNode,0);
                        operationNode.add(nodeFilter.commitNode);
                    }
                }
//                Log.e(TAG, "onAccessibilityEvent: "+nodeInfo);
            }
        }
    }

    /**
     * 初始化
     * 建立相应接受广播
     * 初始化提示
     */
    private void init() {
        Toasty.success(this,"Service Activating").show();
        Log.i(TAG, "init: initialize service...");
        com.byq.applib.broadcast.CommunicateBroadcast communicateBroadcast = new CommunicateBroadcast() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String event = getStringByExtra(intent);
                Log.i(TAG, "onReceive: action : "+event);

                switch (event) {
                    case CALL_RESPONSE_TAG:
                        sendReplay(MainService.this,CALL_RESPONSE_TAG,new Intent());
                        break;

                    case EXPORT_ANSWER_TO_FILE:
                        sendReplay(MainService.this,EXPORT_ANSWER_TO_FILE,new Intent());
                        File file = new File(getFilesDir(), "answer.json");
                        try {
                            file.createNewFile();
                            JSONArray array = new JSONArray();
                            for (Map.Entry<String, QuestionGson> integerQuestionGsonEntry : questionGsonHashMap.entrySet()) {
                                array.put(new JSONObject(GsonUtils.toJson(integerQuestionGsonEntry.getValue())));
                            }
                            FileIOUtils.writeFileFromString(file,array.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        };
        communicateBroadcast.addSupplyEvent(CALL_RESPONSE_TAG);
        communicateBroadcast.addSupplyEvent(EXPORT_ANSWER_TO_FILE);
        communicateBroadcast.register(this);
    }

    @Override
    public void onInterrupt() {
        Toasty.error(this,"错误：Service即将中断").show();
    }
}
