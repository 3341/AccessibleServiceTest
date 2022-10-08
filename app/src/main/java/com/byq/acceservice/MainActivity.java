package com.byq.acceservice;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.GsonUtils;
import com.byq.applib.FileTools;
import com.byq.applib.StatusUtil;
import com.byq.applib.broadcast.CommunicatBroadcastForReplay;
import com.byq.applib.broadcast.CommunicateBroadcast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;

import es.dmoral.toasty.Toasty;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int CONNECT_STATUS_CONNECTED = 0;
    private static final int CONNECT_STATUS_DISCONNECT = 1;
    private static final int CONNECT_STATUS_LOADING = 2;
    private static final int CONNECT_STATUS_NEED_UPDATE = 3;
    private static final int CONNECT_SYNCING = 4;

    private Handler handler = new Handler();
    private ConfigGson configGson;

    private LinearLayout mSystemOkBg;
    private ProgressBar mSystemCallProgress;
    private ImageView mSystemOkIcon;
    private TextView mSystemOkText;
    private TextView mSystemVersionText;
    private MaterialButton mStartAccessibleService;
    private Switch mSwitchOnQuestionIndexing;
    private Switch mSwitchOnQuestionAnswerMode;
    private RelativeLayout mFileUploadedBg;
    private ImageView mFileUploadedIcon;
    private TextView mFileUploadedText;
    private MaterialButton mReceiveQuestionAnswer;
    private MaterialButton mSendQuestionAnswer;
    private Switch mSwitchOnListSearch;
    private TextInputEditText mSearchTextInput;
    private Switch mSwitchOnListExport;
    private TextInputEditText mListRegexInput;




    @SuppressLint("MissingInflatedId")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusUtil.setWhiteStatusBar(this);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(R.layout.activity_main);


        mSystemOkBg = findViewById(R.id.systemOkBg);
        mSystemCallProgress = findViewById(R.id.systemCallProgress);
        mSystemOkIcon = findViewById(R.id.systemOkIcon);
        mSystemOkText = findViewById(R.id.systemOkText);
        mSystemVersionText = findViewById(R.id.systemVersionText);
        mStartAccessibleService = findViewById(R.id.startAccessibleService);
        mSwitchOnQuestionIndexing = findViewById(R.id.switchOnQuestionIndexing);
        mSwitchOnQuestionAnswerMode = findViewById(R.id.switchOnQuestionAnswerMode);
        mFileUploadedBg = findViewById(R.id.fileUploadedBg);
        mFileUploadedIcon = findViewById(R.id.fileUploadedIcon);
        mFileUploadedText = findViewById(R.id.fileUploadedText);
        mReceiveQuestionAnswer = findViewById(R.id.receiveQuestionAnswer);
        mSendQuestionAnswer = findViewById(R.id.sendQuestionAnswer);
        mSwitchOnListSearch = findViewById(R.id.switchOnListSearch);
        mSearchTextInput = findViewById(R.id.searchTextInput);
        mSwitchOnListExport = findViewById(R.id.switchOnListExport);
        mListRegexInput = findViewById(R.id.listRegexInput);


        initConfig();

        startConnect();

        mSystemOkBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConnect();
            }
        });

        mSendQuestionAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommunicateBroadcast.sendBroadcast(MainActivity.this,MainService.EXPORT_ANSWER_TO_FILE,new Intent());
                Toasty.success(MainActivity.this,"OK").show();
            }
        });

        mStartAccessibleService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });

        mSwitchOnQuestionIndexing.setChecked(configGson.qa.enable);
        mSwitchOnQuestionAnswerMode.setChecked(configGson.qa.isAcquireAnswerMode);
        String answer = configGson.qa.answer;
        if (answer == null) answer = "";

        mSwitchOnQuestionAnswerMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                configGson.qa.isAcquireAnswerMode = isChecked;
                updateConfig();
            }
        });

        mSwitchOnQuestionIndexing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                configGson.qa.enable = isChecked;
                updateConfig();
            }
        });

        mSendQuestionAnswer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommunicateBroadcast.sendBroadcast(MainActivity.this,MainService.EXPORT_ANSWER_TO_FILE,new Intent());
                updateConfig();
                Toasty.success(MainActivity.this,"Success").show();
            }
        });
    }

    private File getConfigFile() {
        return new File(getFilesDir(),"configs.json");
    }

    private void createConfigExist() {
        File filesDir = getFilesDir();
        File configFile = new File(filesDir,"configs.json");
        if (!configFile.isFile()) {
            filesDir.mkdirs();
            try {
                configFile.createNewFile();
                FileTools.copyAsset(this,"defaultConfig.json",configFile);
                Toasty.success(this,"初始化成功").show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initConfig() {
        createConfigExist();
        String content = FileIOUtils.readFile2String(getConfigFile());
        ConfigGson configGson = GsonUtils.fromJson(content, ConfigGson.class);
        this.configGson = configGson;
    }

    private void sendUpdateRequest() {
        switchConnectStatus(CONNECT_SYNCING);
        CommunicateBroadcast.sendBroadcast(this, MainService.UPDATE_CONFIG_TAG, new Intent(), new CommunicatBroadcastForReplay(MainService.UPDATE_CONFIG_TAG) {
            @Override
            public void onReplayReceived(Context context, Intent intent) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        switchConnectStatus(CONNECT_STATUS_CONNECTED);
                    }
                },500);
            }

            @Override
            public boolean onReceiveTimeout() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toasty.error(MainActivity.this,"错误：失去连接").show();
                        startConnect();
                    }
                });
                return false;
            }
        });
    }

    private void updateConfig() {
        FileIOUtils.writeFileFromString(getConfigFile(),GsonUtils.toJson(configGson));
        sendUpdateRequest();
    }

    private void startConnect() {
        switchConnectStatus(CONNECT_STATUS_LOADING);
        CommunicateBroadcast.sendBroadcast(this, MainService.CALL_RESPONSE_TAG,
                new Intent(), new CommunicatBroadcastForReplay(MainService.CALL_RESPONSE_TAG) {
            @Override
            public void onReplayReceived(Context context, Intent intent) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toasty.success(MainActivity.this,"Received return message, Accessible Service Active Success",Toasty.LENGTH_LONG).show();
                        switchConnectStatus(CONNECT_STATUS_CONNECTED);
                    }
                },1000);
            }

            @Override
            public boolean onReceiveTimeout() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switchConnectStatus(CONNECT_STATUS_DISCONNECT);
                    }
                });
                return false;
            }
        });
    }

    private void switchConnectStatus(int status) {
        switch (status) {
            case CONNECT_STATUS_LOADING:
                switchConnectStatusBgColor(getColor(R.color.yellow));
                mSystemOkText.setText("无障碍服务连接中");
                mSystemOkIcon.setVisibility(View.GONE);
                mSystemVersionText.setVisibility(View.GONE);
                mSystemCallProgress.setVisibility(View.VISIBLE);
                break;

            case CONNECT_STATUS_DISCONNECT:
                switchConnectStatusBgColor(getColor(R.color.red));
                mSystemOkText.setText("无障碍服务未连接");
                mSystemOkIcon.setVisibility(View.VISIBLE);
                mSystemCallProgress.setVisibility(View.GONE);
                mSystemVersionText.setVisibility(View.GONE);
                mSystemOkIcon.setImageResource(R.drawable.ic_twotone_error_24);
                break;

            case CONNECT_STATUS_CONNECTED:

                switchConnectStatusBgColor(getColor(R.color.green));

                mSystemOkText.setText("无障碍服务已连接");
                mSystemOkIcon.setVisibility(View.VISIBLE);
                mSystemCallProgress.setVisibility(View.GONE);
                mSystemVersionText.setVisibility(View.VISIBLE);
                mSystemOkIcon.setImageResource(R.drawable.ic_twotone_check_circle_24);
                break;


            case CONNECT_SYNCING:
                switchConnectStatusBgColor(getColor(R.color.yellow));
                mSystemOkText.setText("正在同步数据");
                mSystemOkIcon.setVisibility(View.GONE);
                mSystemCallProgress.setVisibility(View.VISIBLE);
                mSystemVersionText.setVisibility(View.VISIBLE);
                mSystemOkIcon.setImageResource(es.dmoral.toasty.R.drawable.ic_error_outline_white_24dp);
                break;
        }
    }

    private void switchConnectStatusBgColor(int color) {
        ValueAnimator valueAnimator = ValueAnimator.ofArgb(mSystemOkBg.getBackgroundTintList().getDefaultColor(), color);
        valueAnimator.setDuration(500);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int color = (int) animation.getAnimatedValue();
                mSystemOkBg.setBackgroundTintList(ColorStateList.valueOf(color));
            }
        });
        valueAnimator.start();
    }
}




