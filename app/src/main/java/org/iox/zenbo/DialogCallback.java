package org.iox.zenbo;

import android.drm.DrmStore;
import android.util.Log;

import com.asus.robotframework.API.RobotCallback;
import com.asus.robotframework.API.RobotUtil;
import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.RobotFace;

public class DialogCallback implements RobotCallback.Listen {
    String TAG = "DialogCallback";
    RobotAPI robotAPI;
    ActionRunnable mActionRunable;

    public void setRobotAPI(RobotAPI robotAPI)
    {
        this.robotAPI = robotAPI;
    }
    public void setActionRunnable(ActionRunnable actionRunnable)
    {
        mActionRunable = actionRunnable;
    }

    public void onRetry(org.json.JSONObject result)
    {
    }

    public void onResult(org.json.JSONObject result)
    {
    }

    public void onEventUserUtterance(org.json.JSONObject result)
    {
        String keyword = RobotUtil.queryListenResultJson( result, "result");
        if(!keyword.equals("")) {
            if (keyword.contains("show face") || keyword.contains("share files")) {
                robotAPI.robot.setExpression(RobotFace.DEFAULT);
            }
            else if (keyword.contains("hide face" ) || keyword.contains("pie face") || keyword.contains("hi face") || keyword.contains("high face")) {
                robotAPI.robot.setExpression(RobotFace.HIDEFACE);
            }
            else if (keyword.contains("sit down") || keyword.contains("shut down")) {
                if (mActionRunable.bDontMove == false) {
                    mActionRunable.bDontMove = true;
                    robotAPI.robot.speak("I don't move.");
                }
            }
            else if (keyword.contains("stand up")) {
                if (mActionRunable.bDontMove == true) {
                    mActionRunable.bDontMove = false;
                    robotAPI.robot.speak("I will move.");
                }
            }
        }
    }

    public void onFinishRegister()
    {
    }

    public void onVoiceDetect(org.json.JSONObject result)
    {
    }

    public void onSpeakComplete(String a ,String b)
    {
    }
}
