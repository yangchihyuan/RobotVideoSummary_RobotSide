package tw.edu.cgu.ai.zenbo;

import com.asus.robotframework.API.RobotCallback;
import com.asus.robotframework.API.RobotUtil;
import com.asus.robotframework.API.RobotAPI;

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
        String keyword = RobotUtil.queryListenResultJson( result, "result");
        if(!keyword.equals("")) {
            //The "show face" command will launch a built-in app.
            //"share the files" is a
            if (keyword.contains("share the files")){
                if( mActionRunable.mShowRobotFace) {
                    mActionRunable.mShowRobotFace = false;
//                    robotAPI.robot.setExpression(RobotFace.HIDEFACE);
                }
                else
                    mActionRunable.mShowRobotFace = true;

                //How to turn off Zenbo's listening immediately? No way.
            }

            if (keyword.contains("take a ride it"))
            {
                if( mActionRunable.getRobotStatus().equals(ActionRunnable.RobotStatus.IDLE) )
                {
                    mActionRunable.setRobotStatus(ActionRunnable.RobotStatus.ACTIVE);
                    robotAPI.robot.speak("ok, let's go.");
                }
            }
            //"hide face" is very difficultly recognized
//            else if (keyword.contains("hide face" ) || keyword.contains("pie face") || keyword.contains("hi face") || keyword.contains("high face") || keyword.contains("go head please")) {
//                mActionRunable.setShowRobotFace(false);
//                robotAPI.robot.setExpression(RobotFace.HIDEFACE);
//            }
//            else if (keyword.contains("sit down") || keyword.contains("shut down")) {
//                if (mActionRunable.bDontMove == false) {
//                    mActionRunable.bDontMove = true;
//                    robotAPI.robot.speak("I don't move.");
//                }
//            }
//            else if (keyword.contains("stand up")) {
//                if (mActionRunable.bDontMove == true) {
//                    mActionRunable.bDontMove = false;
//                    robotAPI.robot.speak("I will move.");
//                }
//            }
        }
    }

    public void onEventUserUtterance(org.json.JSONObject result)
    {
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
