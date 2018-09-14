package org.iox.zenbo;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.asus.robotframework.API.RobotAPI;
import com.asus.robotframework.API.MotionControl;
import com.asus.robotframework.API.RobotCmdState;
import com.asus.robotframework.API.RobotFace;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static java.lang.Math.max;

public class ActionRunnable implements Runnable {
    public int pitchDegree = 30;       //range -15 to 55
    public int yawDegree = 0;          //range -45(left) to 45(right)
    private com.asus.robotframework.API.RobotAPI ZenboAPI;
    private ZenboCallback robotCallback;
    private boolean bWaitingForRobotFinishesMovement = false;
    public boolean bFreezeHead = false;
    public boolean bDontMove = false;
    private MessageView mMessageView_Detection;
    private MessageView mMessageView_Timestamp;
    private DataBuffer dataBuffer;
    private String mTag = "ActionRunnable";
    private boolean mShowRobotFace = false;
    private int robot_status = 1;
    private int number_of_stuck = 0;
    private String mMessage_Detection = "";
    private String mMessage_Timestamp = "";
    private String newline = System.getProperty("line.separator");
    private String[] detection_mode_description = new String[7];
    private boolean mbFirstTime = true;

    private Socket socket_client;
    private final int mPort_Number = 8896;

    public ActionRunnable() {
        bWaitingForRobotFinishesMovement = false;
        detection_mode_description[0]= "no person is found";
        detection_mode_description[1]= "neglect, low probability";
        detection_mode_description[2]= "false positive";
        detection_mode_description[3]= "long distance";
        detection_mode_description[4]= "extremely short distance";
        detection_mode_description[5]= "short distance";
        detection_mode_description[6]= "otherwise";
    }

    public void setMessageView(MessageView MessageView_Detection, MessageView MessageView_Timestamp) {
        mMessageView_Detection = MessageView_Detection;
        mMessageView_Timestamp = MessageView_Timestamp;
        robotCallback = new ZenboCallback();
        ZenboAPI = new RobotAPI(MessageView_Detection.getContext(), robotCallback);
        ZenboAPI.motion.moveHead(yawDegree, pitchDegree, MotionControl.SpeedLevel.Head.L3);
        robotCallback.RobotMovementFinished_Head = false;
        robotCallback.RobotMovementFinished_Body = false;
    }


    public void setDataBuffer(DataBuffer dataBuffer) {
        this.dataBuffer = dataBuffer;
    }

    public void setShowRobotFace(boolean ShowRobotFace) {
        mShowRobotFace = ShowRobotFace;
    }

    @Override
    public void run() {
        if(mbFirstTime) {
            try {
                socket_client = new Socket("pepper.csie.ntu.edu.tw", mPort_Number);
                mbFirstTime = false;
            } catch (Exception e) {
                System.out.println("Socket does not work.");
                System.out.println("IOException :" + e.toString());
            }
        }

        boolean bContinue = true;
        final long timestamp_decision = System.currentTimeMillis();
        //2018/5/18 Chih-Yuan: Because the callback is unstable, I need to manually reset the flag
        if (robotCallback.RobotMovementFinished_Head == false && System.currentTimeMillis() - robotCallback.TimeStamp_MovementHead_Active > 3000) {
            robotCallback.RobotMovementFinished_Head = true;
        }
        if (robotCallback.RobotMovementFinished_Body == false && System.currentTimeMillis() - robotCallback.TimeStamp_MovementBody_Active > 4000) {
            robotCallback.RobotMovementFinished_Body = true;
        }

        if (robotCallback.RobotMovementFinished_Head && robotCallback.RobotMovementFinished_Body) {
            bWaitingForRobotFinishesMovement = false;
        }

        AnalyzedFrame LatestFrame = dataBuffer.getLatestFrame();
        //show messages of action recognition here
        int number_of_actions = LatestFrame.actions.length;
        mMessage_Timestamp = "";
        for(int i=0; i<number_of_actions ; i++)
            mMessage_Timestamp += (LatestFrame.actions[i] + newline);

        if (bWaitingForRobotFinishesMovement) {
            mMessage_Timestamp += "waiting for robot finishing a move";
            bContinue = false;
        }
        else if (dataBuffer.IsDataAvailable() == false) {
            mMessage_Timestamp += "data unavailable";
            bContinue = false;
        }
        else {
            long max_timestamp = dataBuffer.getLatestTimeStamp_OnImageAvailable();
            if (max_timestamp < robotCallback.TimeStamp_MovementFinished_Body ||
                    max_timestamp < robotCallback.TimeStamp_MovementFinished_Head) {
                mMessage_Timestamp += "max_timestamp less than robot movement finished";
                //It doesn't make sense to be continuously stuck in this if statement.
                //It appears that there is something wrong in Zenbo's callback function or the timestamp.
                number_of_stuck++;
                if (number_of_stuck > 10) {
                    //reset the two timestamps of the robotCallback
                    robotCallback.TimeStamp_MovementFinished_Body = 0;
                    robotCallback.TimeStamp_MovementFinished_Head = 0;
                }
                if (dataBuffer.IsDataFrozen() == false)
                    bContinue = false;
            } else {
                //reset the number
                number_of_stuck = 0;
            }
        }

        mMessageView_Timestamp.setString(mMessage_Timestamp);

        if( bContinue) {
//            AnalyzedFrame LatestFrame = dataBuffer.getLatestFrame();
            //show messages of action recognition here
//            mMessage_Timestamp = LatestFrame.actions[0] + newline + LatestFrame.actions[1] + newline + LatestFrame.actions[2];
//            mMessageView_Timestamp.setString(mMessage_Timestamp);

            AverageFrame AverageFrames = dataBuffer.getAverageFrame();
            LatestFrame.bNew = false;
            float distance_d1811 = 0;
            int detection_mode = 0;
            //detection_mode 1: no person is found
            //detection_mode 2: neglect the found person because the probability is low
            //detection_mode 3: found a person, but it is a false positive
            //detection_mode 4: found a person in a long distance
            //detection_mode 5: found a person in an extremely short distance
            //detection_mode 6: found a person in a short distance
            //detection_mode 7: otherwise
            int action_mode = 0;
            //action_mode 1: turn left
            //action_mode 2: turn right
            //action_mode 3: change pitch degree to 15
            //action_mode 4: move the robot's head to put the found person's P1811 center at the image center, and move the robot
            //action_mode 5: move the head up
            //action_mode 6: rotate the robot and adjust its head's pitch angle to track a subject's nose,

            //robot_status 1: active
            //robot_status 2: don't move to save energy

            detection_mode = 0; //initialize
            if (LatestFrame.bFoundPerson == false) {
                detection_mode = 1;
            } else if (LatestFrame.bIgnorePerson) {
                detection_mode = 2;
            }
            else {
                float[][] fMatrix = LatestFrame.fMatrix;
                float[] yMatrix = LatestFrame.yMatrix;      //format:

                float c_w = 640;
                float c_h = 480;
                //convert the yolo output to pixel coorecidate
                float top_x = (yMatrix[0] - yMatrix[2]/2) * c_w;
                float bot_x = (yMatrix[0] + yMatrix[2]/2) * c_w;
                float top_y = (yMatrix[1] + yMatrix[3]/2) * c_h;
                float bot_y = (yMatrix[1] - yMatrix[3]/2) * c_h;

                boolean bNoseInYoloBoundingBox = false;
                if( fMatrix[0][2] > 0 )       //there is a nose keypoint
                {
                    float nose_x = fMatrix[0][0];
                    float nose_y = fMatrix[0][1];
                    if (nose_x > top_x && nose_x < bot_x && nose_y > top_y && nose_y < bot_y)
                        bNoseInYoloBoundingBox = true;
                }
                else
                {
                    detection_mode = 3;     //false postive
                }
            }

            if(detection_mode == 0)
            {
                float[][] fMatrix = LatestFrame.fMatrix;
                float chest_x = fMatrix[1][0];
                float chest_y = fMatrix[1][1];
                float lefthip_x = fMatrix[8][0];
                float lefthip_y = fMatrix[8][1];
                float righthip_x = fMatrix[11][0];
                float righthip_y = fMatrix[11][1];
                robot_status = 1;
                float distance_chest_to_lefthip = (float) Math.sqrt((Math.pow(chest_x - lefthip_x, 2) + Math.pow(chest_y - lefthip_y, 2)));
                float distance_chest_to_righthip = (float) Math.sqrt((Math.pow(chest_x - righthip_x, 2) + Math.pow(chest_y - righthip_y, 2)));
                distance_d1811 = (distance_chest_to_lefthip + distance_chest_to_righthip) / 2;
                if (distance_d1811 <= 131.7489 && pitchDegree < 45 && AverageFrames.bValid_1811) {
                    //                    dataBuffer.FreezeData();
                    detection_mode = 4;
                } else {
                    if (fMatrix[1][2] > 0 && fMatrix[8][2] > 0 && fMatrix[11][2] > 0 && fMatrix[0][2] == 0)
                        detection_mode = 5;
                    else if ((fMatrix[0][2] > 0))
                        detection_mode = 6;
                    else
                        detection_mode = 7;
                }
            }

            mMessage_Detection = "detection_mode:" + Integer.toString(detection_mode) + " "+detection_mode_description[detection_mode-1] + " pitchDegree:" + Integer.toString(pitchDegree);

            if (robot_status == 1 && (detection_mode == 1 || detection_mode == 2)) {
                boolean bTurnOneAround = dataBuffer.CheckTurnOneAround();
                boolean bTurnTwoAround = dataBuffer.CheckTurnTwoAround();
                if (bTurnTwoAround) {
                    //stop, don't turn.
                    robot_status = 2;
                } else if (bTurnOneAround) {
                    //change pitch degree
                    pitchDegree = 15;
                    ZenboAPI.motion.moveHead(yawDegree, pitchDegree, MotionControl.SpeedLevel.Head.L3);
                    bWaitingForRobotFinishesMovement = true;
                    robotCallback.RobotMovementFinished_Head = false;
                    robotCallback.TimeStamp_MovementFinished_Head = Long.MAX_VALUE;
                    action_mode = 3;
                    dataBuffer.AddAnAction(action_mode);
                } else {
                    float relocate_y = 0;
                    float relocate_x = 0;
                    int turn_direction = dataBuffer.CheckMostRecentData_PersonAtLeftOrRight();
                    float horizontal_center_shift_degree = 0;
                    if (turn_direction == 2)        //turn_direction == 2 means turn left
                    {
                        horizontal_center_shift_degree = 30f;
//                        Log.d("turn_direction", "turn left");
                        action_mode = 1;
                        dataBuffer.AddAnAction(action_mode);
                        if (mShowRobotFace)
                            ZenboAPI.robot.setExpression(RobotFace.AWARE_LEFT);
                        else {

                        }
                    } else if (turn_direction == 1)   //turn_direction == 1 means turn right
                    {
                        horizontal_center_shift_degree = -30f;
//                        Log.d("turn_direction", "turn right");
                        action_mode = 2;
                        dataBuffer.AddAnAction(action_mode);
                        if (mShowRobotFace)
                            ZenboAPI.robot.setExpression(RobotFace.AWARE_RIGHT);
                        else {

                        }
                    } else if (turn_direction == 0)        //the robot hasn't seen any person yet
                    {
                        horizontal_center_shift_degree = -30f;
//                        Log.d("turn_direction", "turn right");
                        action_mode = 2;
                        dataBuffer.AddAnAction(action_mode);
                        if (mShowRobotFace)
                            ZenboAPI.robot.setExpression(RobotFace.AWARE_RIGHT);
                        else {

                        }
                    }

                    ZenboAPI.motion.moveBody(relocate_x, relocate_y, Math.round(horizontal_center_shift_degree));
                    bWaitingForRobotFinishesMovement = true;
                    robotCallback.RobotMovementFinished_Body = false;
                    robotCallback.TimeStamp_MovementFinished_Body = Long.MAX_VALUE;
                }
            }

            if (detection_mode == 4) {
                action_mode = 4;
                dataBuffer.AddAnAction(action_mode);
                //before moving body, place the head up right
                //from chest_x to compute the theta, place it in the center
                //Zenbo's horizotal view angle: 62.5053
                float[][] fMatrix = LatestFrame.fMatrix;
                float chest_x = fMatrix[1][0];
                float chest_y = fMatrix[1][1];
                float lefthip_y = fMatrix[8][1];
                float righthip_y = fMatrix[11][1];
                int relativeThetaDegree = 0;
                float ThetaOfBodyCenterOnImage = -(chest_x - 320) / 640 * 62.5053f;
                float ThetaOfBodyCenterOnImageAfterMoveHeadUpRight = ThetaOfBodyCenterOnImage + yawDegree;
                yawDegree = 0;
                relativeThetaDegree = Math.round(ThetaOfBodyCenterOnImageAfterMoveHeadUpRight);
                //control the pitchDegree so that the chest will be at the center rather than the nose
                float y1811 = 0.5f * chest_y + 0.25f * lefthip_y + 0.25f * righthip_y;
                float vertical_center_shift = y1811 - 240;
                float vertical_center_shift_degree = vertical_center_shift / 10.2f;     //the value 10.2 is computed from experiments
                pitchDegree = pitchDegree - Math.round(vertical_center_shift_degree);        //range -15 to 55, Zenbo's vertical view angle is 48.9336
                if (pitchDegree > 55)
                    pitchDegree = 55;
                if (pitchDegree < -15)
                    pitchDegree = -15;

                ZenboAPI.motion.moveHead(yawDegree, pitchDegree, MotionControl.SpeedLevel.Head.L3);
                bWaitingForRobotFinishesMovement = true;
                robotCallback.RobotMovementFinished_Head = false;
                robotCallback.TimeStamp_MovementFinished_Head = Long.MAX_VALUE;

                float distance_physical = 0;
                float distance_forward = 0;
                if (distance_d1811 >= 158.8841) {
                    //do not thing, don't move.
                } else if (distance_d1811 >= 131.7489 && distance_d1811 < 158.8841) {
                    float x1 = 158.8841f;
                    float y1 = 150 - 50;        //-50 for conservation
                    float x2 = 131.7489f;
                    float y2 = 210 - 50;        //-50 for conservation
                    distance_physical = y2 + (distance_d1811 - x1) * (y1 - y2) / (x1 - x2);
                    distance_forward = (max(0, distance_physical - 150)) / 100; //unit: meter
                } else if (distance_d1811 >= 96.9894 && distance_d1811 < 131.7489) {
                    float x1 = 131.7489f;
                    float y1 = 210 - 50;             //-50 for conservation
                    float x2 = 96.9894f;
                    float y2 = 270 - 50;            //-50 for conservation
                    distance_physical = y2 + (distance_d1811 - x1) * (y1 - y2) / (x1 - x2);
                    distance_forward = (distance_physical - 150) / 100; //unit: meter
                } else if (distance_d1811 >= 73.1422 && distance_d1811 < 96.9894) {
                    float x1 = 96.9894f;
                    float y1 = 270 - 50;        //-50 for conservation
                    float x2 = 73.1422f;
                    float y2 = 400 - 50;        //-50 for conservation
                    distance_physical = y2 + (distance_d1811 - x1) * (y1 - y2) / (x1 - x2);
                    distance_forward = (distance_physical - 150) / 100; //unit: meter
                } else {
                    distance_physical = 400;
                    distance_forward = (distance_physical - 150) / 100; //unit: meter
                }

                if (bDontMove)      //a special flag to record training data
                    distance_forward = 0;

                //from the relativeThetaDegree and distance_forward, to compute the new position
                double relativeThetaRadian = relativeThetaDegree / 180 * 3.14d;
                float relocate_y = distance_forward * (float) Math.sin(relativeThetaRadian);
                float relocate_x = distance_forward * (float) Math.cos(relativeThetaRadian);
                if (relocate_x != 0 || relocate_y != 0 || relativeThetaRadian != 0) {
                    ZenboAPI.motion.moveBody(relocate_x, relocate_y, relativeThetaDegree);
                    bWaitingForRobotFinishesMovement = true;
                    robotCallback.RobotMovementFinished_Body = false;
                    robotCallback.TimeStamp_MovementFinished_Body = Long.MAX_VALUE;
                    if (mShowRobotFace)
                        ZenboAPI.robot.setExpression(RobotFace.ACTIVE);
                    else {

                    }
                }
            }

            if (detection_mode == 5) {
                action_mode = 5;
                float[][] fMatrix = LatestFrame.fMatrix;
                float chest_y = fMatrix[1][1];
                dataBuffer.AddAnAction(action_mode);
                float expected_nose_y = chest_y - 50;
                float vertical_center_shift = expected_nose_y - 240;
                float vertical_center_shift_degree = vertical_center_shift / 10.2f;     //the value 10.2 is computed from experiments
                pitchDegree = pitchDegree - Math.round(vertical_center_shift_degree);        //range -15 to 55, Zenbo's vertical view angle is 48.9336
                if (pitchDegree > 55)
                    pitchDegree = 55;
                if (pitchDegree < -15)
                    pitchDegree = -15;

                ZenboAPI.motion.moveHead(yawDegree, pitchDegree, MotionControl.SpeedLevel.Head.L3);
                bWaitingForRobotFinishesMovement = true;
                robotCallback.RobotMovementFinished_Head = false;
                robotCallback.TimeStamp_MovementFinished_Head = Long.MAX_VALUE;
                if (mShowRobotFace)
                    ZenboAPI.robot.setExpression(RobotFace.PLEASED);
                else {
                    //                    ZenboAPI.robot.setExpression(RobotFace.HIDEFACE);
                }
            }
            //        }

            //Track nose mode
            if (detection_mode == 6) {
                action_mode = 6;
                dataBuffer.AddAnAction(action_mode);
                float[][] fMatrix = LatestFrame.fMatrix;
                float y = fMatrix[0][1];
                float vertical_center_shift = y - 240;
                float vertical_center_shift_degree = vertical_center_shift / 10.2f;     //the value 10.2 is computed from experiments
                if (!bFreezeHead)
                    if (vertical_center_shift_degree > 5 || vertical_center_shift_degree < -5) {
                        pitchDegree = pitchDegree - Math.round(vertical_center_shift_degree);        //range -15 to 55, Zenbo's vertical view angle is 48.9336
                        if (pitchDegree > 55)
                            pitchDegree = 55;
                        if (pitchDegree < -15)
                            pitchDegree = -15;
                        ZenboAPI.motion.moveHead(yawDegree, pitchDegree, MotionControl.SpeedLevel.Head.L3);
                        bWaitingForRobotFinishesMovement = true;
                        robotCallback.RobotMovementFinished_Head = false;
                        robotCallback.TimeStamp_MovementFinished_Head = Long.MAX_VALUE;
                    }
                float x = fMatrix[0][0];
                float horizontal_center_shift_degree = -(x - 320) / 640 * 62.5053f;
                if (horizontal_center_shift_degree > 5 || horizontal_center_shift_degree < -5) {
                    float relocate_y = 0;
                    float relocate_x = 0;
                    ZenboAPI.motion.moveBody(relocate_x, relocate_y, Math.round(horizontal_center_shift_degree));
                    bWaitingForRobotFinishesMovement = true;
                    robotCallback.RobotMovementFinished_Body = false;
                    robotCallback.TimeStamp_MovementFinished_Body = Long.MAX_VALUE;
                }
                if (mShowRobotFace)
                    ZenboAPI.robot.setExpression(RobotFace.HAPPY);
                else {

                }
            }
//            Log.d(mTag, "detection_mode " + Integer.toString(detection_mode));
            mMessageView_Detection.setString(mMessage_Detection);
        }
    }//end of run

}
