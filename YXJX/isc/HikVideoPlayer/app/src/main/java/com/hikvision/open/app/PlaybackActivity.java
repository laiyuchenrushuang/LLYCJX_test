package com.hikvision.open.app;

import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.hikvision.open.app.widget.AutoHideView;
import com.hikvision.open.app.widget.PlayWindowContainer;
import com.hikvision.open.hikvideoplayer.CustomRect;
import com.hikvision.open.hikvideoplayer.HikVideoPlayer;
import com.hikvision.open.hikvideoplayer.HikVideoPlayerCallback;
import com.hikvision.open.hikvideoplayer.HikVideoPlayerFactory;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Calendar;

/**
 * 错误码开头：17是mgc或媒体取流SDK的错误，18是vod，19是dac
 */
public class PlaybackActivity extends AppCompatActivity implements View.OnClickListener, HikVideoPlayerCallback, TextureView.SurfaceTextureListener {
    private static final String TAG = "PlaybackActivity";
//    private static final String playbackUri = "rtsp://120.236.240.174:554/openUrl/IkuEDCg";
    private static final String playbackUri = "rtsp://11.121.35.88:554/openUrl/nlDoYpO?beginTime=20201222T162643&endTime=20201222T162722";
    protected PlayWindowContainer frameLayout;
    protected TextureView textureView;
    protected ProgressBar progressBar;
    protected TextView playHintText;
    protected TextView digitalScaleText;
    protected AutoHideView autoHideView;
    protected Button captureButton;
    protected Button recordButton;
    protected Button soundButton;
    protected Button pauseButton;
    protected TimeBarView timeBar;
    protected TextInputLayout textInputLayout;
    protected EditText playbackUriEdit;
    protected Button start;
    protected Button stop;
    protected SwitchCompat decodeSwitch;
    protected SwitchCompat smartSwitch;
    private TextView mRecordFilePathText;
    private String mUri;
    private HikVideoPlayer mPlayer;
    private boolean mSoundOpen = false;
    private boolean mRecording = false;
    private boolean mPausing = false;
    private boolean mDigitalZooming = false;
    /*回放开始时间*/
    private Calendar mStartCalendar;
    /*回放结束时间*/
    private Calendar mEndCalendar;
    /*回放定位时间*/
    private Calendar mSeekCalendar = Calendar.getInstance();
    private PlayerStatus mPlayerStatus = PlayerStatus.IDLE;//默认闲置
    /**
     * 电子放大倍数格式化,显示小数点后一位
     */
    private DecimalFormat decimalFormat;
    /**
     * 每隔400ms获取一次当前回放的系统时间
     * 更新时间条上的OSD时间
     */
    private final Runnable mGetOSDTimeTask = new Runnable() {
        @Override
        public void run() {
            long osdTime = mPlayer.getOSDTime();
            if (osdTime > -1) {
                timeBar.setCurrentTime(osdTime);
            }

            startUpdateTime();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);//防止键盘弹出
        setContentView(R.layout.activity_playback);
        initView();
        initTimeBarView();
        initPlayWindowContainer();
        mPlayer = HikVideoPlayerFactory.provideHikVideoPlayer();
        //设置默认值
        mPlayer.setHardDecodePlay(decodeSwitch.isChecked());
        mPlayer.setSmartDetect(smartSwitch.isChecked());
    }

    private void initView() {
        textureView = findViewById(R.id.texture_view);
        progressBar = findViewById(R.id.progress_bar);
        playHintText = findViewById(R.id.result_hint_text);
        digitalScaleText = findViewById(R.id.digital_scale_text);
        autoHideView = findViewById(R.id.auto_hide_view);
        captureButton = findViewById(R.id.capture_button);
        mRecordFilePathText = findViewById(R.id.record_file_path_text);
        captureButton.setOnClickListener(this);
        recordButton = findViewById(R.id.record_button);
        recordButton.setOnClickListener(this);
        soundButton = findViewById(R.id.sound_button);
        soundButton.setOnClickListener(this);
        pauseButton = findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(PlaybackActivity.this);
        timeBar = findViewById(R.id.time_bar);
        textInputLayout = findViewById(R.id.playback_input_layout);
        playbackUriEdit = findViewById(R.id.playback_uri_edit);
        start = findViewById(R.id.start);
        start.setOnClickListener(PlaybackActivity.this);
        stop = findViewById(R.id.stop);
        stop.setOnClickListener(PlaybackActivity.this);
        textureView.setSurfaceTextureListener(this);
        playbackUriEdit.setText(playbackUri);
        decodeSwitch = findViewById(R.id.decode_switch);
        smartSwitch = findViewById(R.id.smart_switch);
        //默认为软件解码，硬件解码时，智能信息会不显示
        decodeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mPlayerStatus == PlayerStatus.LOADING || mPlayerStatus == PlayerStatus.SUCCESS){
                    //播放加载过程中和正在播放时，不可以点击
                    ToastUtils.showShort("此设置必须要在播放前设置");
                    decodeSwitch.setChecked(!isChecked);
                    return;
                }
                ToastUtils.showShort(isChecked ? "硬解码开" : "硬解码关");
                mPlayer.setHardDecodePlay(isChecked);
            }
        });
        //默认关闭智能信息展示，智能信息包括智能分析、移动侦测、热成像信息、温度信息等
        smartSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mPlayerStatus == PlayerStatus.LOADING || mPlayerStatus == PlayerStatus.SUCCESS){
                    //播放加载过程中和正在播放时，不可以点击
                    ToastUtils.showShort("此设置必须要在播放前设置");
                    smartSwitch.setChecked(!isChecked);
                    return;
                }
                ToastUtils.showShort(isChecked ? "智能信息开" : "智能信息关");
                mPlayer.setSmartDetect(isChecked);
            }
        });
    }

    private void initPlayWindowContainer() {
        frameLayout = findViewById(R.id.frame_layout);
        //设置点击监听
        frameLayout.setOnClickListener(new PlayWindowContainer.OnClickListener() {
            @Override
            public void onSingleClick() {
                if (autoHideView.isVisible()) {
                    autoHideView.hide();
                } else {
                    autoHideView.show();
                }
            }
        });
        //设置电子放大开启监听
        frameLayout.setOnDigitalListener(new PlayWindowContainer.OnDigitalZoomListener() {
            @Override
            public void onDigitalZoomOpen() {
                executeDigitalZoom();
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.capture_button) {
            executeCaptureEvent();
        } else if (view.getId() == R.id.record_button) {
            executeRecordEvent();
        } else if (view.getId() == R.id.sound_button) {
            executeSoundEvent();
        } else if (view.getId() == R.id.pause_button) {
            executePauseEvent();
        } else if (view.getId() == R.id.start) {
            if (mPlayerStatus != PlayerStatus.SUCCESS && getPlaybackUri()) {
                startPlayback(textureView.getSurfaceTexture());
            }
        } else if (view.getId() == R.id.stop) {
            if (mPlayerStatus == PlayerStatus.SUCCESS) {
                mPlayerStatus = PlayerStatus.IDLE;//释放这个窗口
                mRecordFilePathText.setText(null);
                progressBar.setVisibility(View.GONE);
                playHintText.setVisibility(View.VISIBLE);
                playHintText.setText("");
                cancelUpdateTime();
                resetExecuteState();
                mPlayer.stopPlay();
            }
        }
    }

    private boolean getPlaybackUri() {
        mUri = playbackUriEdit.getText().toString();
        if (TextUtils.isEmpty(mUri)) {
            playbackUriEdit.setError("URI不能为空");
            return false;
        }

        if (!mUri.contains("rtsp")) {
            playbackUriEdit.setError("非法URI");
            return false;
        }

        return true;
    }


    private void initTimeBarView() {
        RecordSegment recordSegment = new RecordSegment();
        //这里是模拟的假数据
        recordSegment.setBeginTime("2019-12-25T00:00:00.000+08:00");
        recordSegment.setEndTime("2019-12-25T16:41:04.000+08:00");
        //TODO 注意:TimeBarView中数据为你从服务器端获取到的录像片段列表
        timeBar.addFileInfoList(Arrays.asList(recordSegment));
        timeBar.setTimeBarCallback(new TimeBarView.TimePickedCallBack() {
            @Override
            public void onMoveTimeCallback(long currentTime) {

            }

            @Override
            public void onBarMoving(long currentTime) {

            }

            @Override
            public void onTimePickedCallback(long currentTime) {
                //TODO 注意: 定位操作的时间要在录像片段开始时间和结束时间之内，不再范围内不要执行以下操作
                mSeekCalendar.setTimeInMillis(currentTime);
                Log.e(TAG, "onTimePickedCallback: currentTime = " + CalendarUtil.calendarToyyyy_MM_dd_T_HH_mm_SSSZ(mSeekCalendar));
//                AbsTime start = CalendarUtil.calendarToABS(mSeekCalendar);
                String start = CalendarUtil.calendarToyyyy_MM_dd_T_HH_mm_SSSZ(mSeekCalendar);
                progressBar.setVisibility(View.VISIBLE);
                new Thread(() -> {
                    cancelUpdateTime();//seek时停止刷新时间
//                    if (!mPlayer.seekAbsPlayback(start, PlaybackActivity.this)) {
//                        onPlayerStatus(Status.FAILED, mPlayer.getLastError());
//                    }
                    //TODO:iSC V1.2.0平台开始推荐使用 yyyy_MM_dd_T_HH_mm_SSSZ的起止时间格式
                    if (!mPlayer.seekAbsPlayback(start, PlaybackActivity.this)) {
                        onPlayerStatus(Status.FAILED, mPlayer.getLastError());
                    }
                }).start();
            }

            @Override
            public void onMaxScale() {

            }

            @Override
            public void onMinScale() {

            }
        });
    }


    /**
     * 执行抓图事件
     */
    private void executeCaptureEvent() {
        if (mPlayerStatus != PlayerStatus.SUCCESS) {
            ToastUtils.showShort("没有视频在播放");
        }

        //抓图
        if (mPlayer.capturePicture(MyUtils.getCaptureImagePath(this))) {
            ToastUtils.showShort("抓图成功");
        }
    }

    /**
     * 执行录像事件
     */
    private void executeRecordEvent() {
        if (mPlayerStatus != PlayerStatus.SUCCESS) {
            ToastUtils.showShort("没有视频在播放");
        }

        if (!mRecording) {
            //开始录像
            mRecordFilePathText.setText(null);
            String path = MyUtils.getLocalRecordPath(this);
            if (mPlayer.startRecord(path)) {
                ToastUtils.showShort("开始录像");
                mRecording = true;
                recordButton.setText(R.string.close_record);
                mRecordFilePathText.setText(MessageFormat.format("当前本地录像路径:{0}", path));
            }
        } else {
            //关闭录像
            mPlayer.stopRecord();
            ToastUtils.showShort("关闭录像");
            mRecording = false;
            recordButton.setText(R.string.start_record);
        }
    }

    /**
     * 执行声音开关事件
     */
    private void executeSoundEvent() {
        if (mPlayerStatus != PlayerStatus.SUCCESS) {
            ToastUtils.showShort("没有视频在播放");
        }

        if (!mSoundOpen) {
            //打开声音
            if (mPlayer.enableSound(true)) {
                ToastUtils.showShort("声音开");
                mSoundOpen = true;
                soundButton.setText(R.string.sound_close);
            }
        } else {
            //关闭声音
            if (mPlayer.enableSound(false)) {
                ToastUtils.showShort("声音关");
                mSoundOpen = false;
                soundButton.setText(R.string.sound_open);
            }
        }
    }

    /**
     * 执行播放暂停和恢复播放事件
     */
    private void executePauseEvent() {
        if (mPlayerStatus != PlayerStatus.SUCCESS) {
            ToastUtils.showShort("没有视频在播放");
        }

        if (!mPausing) {
            //暂停播放
            if (mPlayer.pause()) {
                ToastUtils.showShort("暂停播放");
                mPausing = true;
                pauseButton.setText(R.string.resume);
            }
        } else {
            //恢复播放
            if (mPlayer.resume()) {
                ToastUtils.showShort("恢复播放");
                mPausing = false;
                pauseButton.setText(R.string.pause);
            }
        }
    }

    /**
     * 执行电子放大操作
     */
    private void executeDigitalZoom(){
        if (mPlayerStatus != PlayerStatus.SUCCESS) {
            ToastUtils.showShort("没有视频在播放");
        }
        if (decimalFormat == null){
            decimalFormat = new DecimalFormat("0.0");
        }

        if (!mDigitalZooming){
            frameLayout.setOnScaleChangeListener(new PlayWindowContainer.OnDigitalScaleChangeListener() {
                @Override
                public void onDigitalScaleChange(float scale) {
                    Log.i(TAG,"onDigitalScaleChange scale = "+scale);
                    if (scale < 1.0f && mDigitalZooming){
                        //如果已经开启了电子放大且倍率小于1就关闭电子放大
                        executeDigitalZoom();
                    }
                    if (scale>= 1.0f){
                        digitalScaleText.setText(MessageFormat.format("{0}X",decimalFormat.format(scale)));
                    }
                }

                @Override
                public void onDigitalRectChange(CustomRect oRect, CustomRect curRect) {
                    mPlayer.openDigitalZoom(oRect, curRect);
                }
            });
            ToastUtils.showShort("电子放大开启");
            mDigitalZooming = true;
            digitalScaleText.setVisibility(View.VISIBLE);
            digitalScaleText.setText(MessageFormat.format("{0}X",decimalFormat.format(1.0f)));
        }else {
            ToastUtils.showShort("电子放大关闭");
            mDigitalZooming = false;
            digitalScaleText.setVisibility(View.GONE);
            frameLayout.setOnScaleChangeListener(null);
            mPlayer.closeDigitalZoom();
        }
    }

    /**
     * 重置所有的操作状态
     */
    private void resetExecuteState(){
        if (mPausing) {
            mPausing = false;
            pauseButton.setText(R.string.pause);
        }
        if (mDigitalZooming){
            executeDigitalZoom();
        }
        if (mSoundOpen){
            executeSoundEvent();
        }
        if (mRecording){
            executeRecordEvent();
        }
        frameLayout.setAllowOpenDigitalZoom(false);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        layoutViews();
    }

    /**
     * 屏幕方向变化后重新布局View
     */
    private void layoutViews() {
        ViewGroup.LayoutParams layoutParams = frameLayout.getLayoutParams();
        if (ScreenUtils.isPortrait()) {
            //先显示系统状态栏
            showSystemUI();
            //再显示控制按钮区域
            layoutParams.height = SizeUtils.dp2px(250f);
            showOrHideControlArea(true);
        } else if (ScreenUtils.isLandscape()) {
            //隐藏系统UI
            hideSystemUI();
            showOrHideControlArea(false);
            layoutParams.height = ScreenUtils.getScreenHeight()-SizeUtils.dp2px(60f);
        }
    }

    /**
     * 显示或隐藏控制区域
     *
     * @param isShow true-显示，false-隐藏
     */
    private void showOrHideControlArea(boolean isShow) {
        int visibility = isShow ? View.VISIBLE : View.GONE;
        captureButton.setVisibility(visibility);
        recordButton.setVisibility(visibility);
        soundButton.setVisibility(visibility);
        pauseButton.setVisibility(visibility);
        textInputLayout.setVisibility(visibility);
        start.setVisibility(visibility);
        stop.setVisibility(visibility);
        decodeSwitch.setVisibility(visibility);
        smartSwitch.setVisibility(visibility);
        mRecordFilePathText.setVisibility(visibility);
    }

    /**
     * 隐藏系统ui
     */
    private void hideSystemUI() {
        //隐藏ActionBar 如果使用了NoActionBar的Theme则不需要隐藏actionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        //TODO：View.setSystemUiVisibility(int visibility)中，visibility是Mode与Layout任意取值的组合，可传入的实参为：

        // Mode属性
        //View.SYSTEM_UI_FLAG_LOW_PROFILE：状态栏显示处于低能显示状态(low profile模式)，状态栏上一些图标显示会被隐藏。
        //View.SYSTEM_UI_FLAG_FULLSCREEN：Activity全屏显示，且状态栏被隐藏覆盖掉。等同于（WindowManager.LayoutParams.FLAG_FULLSCREEN）
        //View.SYSTEM_UI_FLAG_HIDE_NAVIGATION：隐藏虚拟按键(导航栏)。有些手机会用虚拟按键来代替物理按键。
        //View.SYSTEM_UI_FLAG_IMMERSIVE：这个flag只有当设置了SYSTEM_UI_FLAG_HIDE_NAVIGATION才起作用。
        // 如果没有设置这个flag，任意的View相互动作都退出SYSTEM_UI_FLAG_HIDE_NAVIGATION模式。如果设置就不会退出。
        //View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY：这个flag只有当设置了SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_HIDE_NAVIGATION 时才起作用。
        // 如果没有设置这个flag，任意的View相互动作都坏退出SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_HIDE_NAVIGATION模式。如果设置就不受影响。

        // Layout属性
        //View.SYSTEM_UI_FLAG_LAYOUT_STABLE： 保持View Layout不变，隐藏状态栏或者导航栏后，View不会拉伸。
        //View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN：让View全屏显示，Layout会被拉伸到StatusBar下面，不包含NavigationBar。
        //View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION：让View全屏显示，Layout会被拉伸到StatusBar和NavigationBar下面。
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
        //解决在华为手机上横屏时，状态栏不消失的问题
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); //隐藏状态栏

    }

    /**
     * 显示系统UI
     */
    private void showSystemUI() {
        //显示ActionBar 如果使用了NoActionBar的Theme则不需要显示actionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // This snippet shows the system bars. It does this by removing all the flags
            // except for the ones that make the content appear under the system bars.
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        //解决在华为手机上横屏时，状态栏不消失的问题
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); //显示状态栏
    }


    @Override
    protected void onResume() {
        super.onResume();
        //TODO 注意:APP前后台切换时 SurfaceTextureListener可能在有某些 华为手机 上不会回调，例如：华为P20，所以我们在这里手动调用
        if (textureView.isAvailable()) {
            Log.e(TAG, "onResume: onSurfaceTextureAvailable");
            onSurfaceTextureAvailable(textureView.getSurfaceTexture(), textureView.getWidth(), textureView.getHeight());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //TODO 注意:APP前后台切换时 SurfaceTextureListener可能在有某些 华为手机 上不会回调，例如：华为P20，所以我们在这里手动调用
        if (textureView.isAvailable()) {
            Log.e(TAG, "onPause: onSurfaceTextureDestroyed");
            onSurfaceTextureDestroyed(textureView.getSurfaceTexture());
        }
    }

    /**
     * 开始回放
     */
    private void startPlayback(SurfaceTexture surface) {
        progressBar.setVisibility(View.VISIBLE);
        playHintText.setVisibility(View.GONE);
        mPlayer.setSurfaceTexture(surface);
        //TODO 注意: 开始时间为你从服务端获取的录像片段列表中第一个片段的开始时间，结束时间为录像片段列表的最后一个片段的结束时间
        long startLongTime = CalendarUtil.getDefaultStartCalendar().getTimeInMillis();
        mStartCalendar = Calendar.getInstance();
        mEndCalendar = Calendar.getInstance();
        long endTime = CalendarUtil.getCurDayEndTime(startLongTime);
        mStartCalendar.setTimeInMillis(startLongTime);
        mEndCalendar.setTimeInMillis(endTime);
//        AbsTime startTimeST = CalendarUtil.calendarToABS(mStartCalendar);
//        AbsTime stopTimeST = CalendarUtil.calendarToABS(mEndCalendar);
        String startTime = CalendarUtil.calendarToyyyy_MM_dd_T_HH_mm_SSSZ(mStartCalendar);
        String stopTime = CalendarUtil.calendarToyyyy_MM_dd_T_HH_mm_SSSZ(mEndCalendar);

        //TODO 注意: startPlayback() 方法会阻塞当前线程，需要在子线程中执行,建议使用RxJava
        new Thread(() -> {
            //TODO 注意: 不要通过判断 startPlayback() 方法返回 true 来确定播放成功，播放成功会通过HikVideoPlayerCallback回调，startPlayback() 方法返回 false 即代表 播放失败;
            //TODO 注意: seekTime 参数可以为NULL，表示无需定位到指定时间开始播放。
//            if (!mPlayer.startPlayback(mUri, startTimeST, stopTimeST, PlaybackActivity.this)) {
//                onPlayerStatus(Status.FAILED, mPlayer.getLastError());
//            }
            //TODO:iSC V1.2.0平台开始推荐使用 yyyy_MM_dd_T_HH_mm_SSSZ的起止时间格式
            if (!mPlayer.startPlayback(mUri, startTime, stopTime, PlaybackActivity.this)) {
                onPlayerStatus(Status.FAILED, mPlayer.getLastError());
            }
        }).start();
    }


    /**
     * 播放结果回调
     *
     * @param status    共四种状态：SUCCESS（播放成功）、FAILED（播放失败）、EXCEPTION（取流异常）、FINISH（录像回放结束）
     * @param errorCode 错误码，只有 FAILED 和 EXCEPTION 才有值
     */
    @Override
    @WorkerThread
    public void onPlayerStatus(@NonNull Status status, int errorCode) {
        //TODO 注意: 由于 HikVideoPlayerCallback 是在子线程中进行回调的，所以一定要切换到主线程处理UI
        Log.d(TAG, "onPlayerStatus: 画面回调回来了");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
                //只有播放成功时，才允许开启电子放大
                frameLayout.setAllowOpenDigitalZoom(status == Status.SUCCESS);
                switch (status) {
                    case SUCCESS:
                        //播放成功
                        mPlayerStatus = PlayerStatus.SUCCESS;
                        playHintText.setVisibility(View.GONE);
                        textureView.setKeepScreenOn(true);//保持亮屏
                        timeBar.setCurrentTime(mPlayer.getOSDTime());
                        startUpdateTime();//开始刷新回放时间
                        break;
                    case FAILED:
                        //播放失败
                        mPlayerStatus = PlayerStatus.FAILED;
                        playHintText.setVisibility(View.VISIBLE);
                        playHintText.setText(MessageFormat.format("回放失败，错误码：{0}", Integer.toHexString(errorCode)));
                        break;
                    case EXCEPTION:
                        //取流异常
                        mPlayerStatus = PlayerStatus.EXCEPTION;
                        Log.d(TAG, "onPlayerStatus: 取流出现异常了");

                        mPlayer.stopPlay();//TODO 注意:异常时关闭取流
                        playHintText.setVisibility(View.VISIBLE);
                        playHintText.setText(MessageFormat.format("取流发生异常，错误码：{0}", Integer.toHexString(errorCode)));
                        break;
                    case FINISH:
                        //录像回放结束
                        mPlayerStatus = PlayerStatus.FINISH;
                        ToastUtils.showShort("没有录像片段了");
                        break;
                }
            }
        });
    }


    /**
     * 开始刷新回放时间
     */
    private void startUpdateTime() {
        playHintText.getHandler().postDelayed(mGetOSDTimeTask, 400);
    }

    /**
     * 停止刷新回放时间
     */
    private void cancelUpdateTime() {
        playHintText.getHandler().removeCallbacks(mGetOSDTimeTask);
    }


    /*************************TextureView.SurfaceTextureListener 接口的回调方法********************/
    //TODO 注意:APP前后台切换时 SurfaceTextureListener可能在有某些华为手机上不会回调，例如：华为P20，因此我们需要在Activity生命周期中手动调用回调方法
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (mPlayerStatus == PlayerStatus.STOPPING) {
            //恢复处于暂停播放状态的窗口
            startPlayback(textureView.getSurfaceTexture());
            Log.d(TAG, "onSurfaceTextureAvailable: startPlayback");
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mPlayerStatus == PlayerStatus.SUCCESS) {
            mPlayerStatus = PlayerStatus.STOPPING;//暂停播放，再次进入时恢复播放
            mPlayer.stopPlay();
            cancelUpdateTime();
            Log.d(TAG, "onSurfaceTextureDestroyed: stopPlay");
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

}
