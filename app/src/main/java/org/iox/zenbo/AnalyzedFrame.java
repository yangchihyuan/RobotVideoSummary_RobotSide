package org.iox.zenbo;

import java.util.ArrayList;
import java.util.List;

public class AnalyzedFrame {
    long timestamp_OnImageAvailable;
    long timestamp_ReceivedFromServer;
    int pitchDegree;
    boolean bNew = false;
    int openpose_cnt;
    int yolo_cnt;
    List<float[][]> openpose_coordinate;
    List<float[]> yolo_coordinate;
    float[][] fMatrix;
    float[] yMatrix;
    boolean bFoundPerson = false;
    boolean bIgnorePerson = false;
    boolean bAvailable = false;
    String[] actions = {"",""};     //8/23/2018 Chih-Yuan: I need to modify this statement later

    public AnalyzedFrame()
    {
        fMatrix = new float[18][3];
        openpose_coordinate = new ArrayList<float[][]>();
        yolo_coordinate = new ArrayList<float[]>();
    }

    private int find_max_value_in_numeric_array_with_java ( float[] numbers) {
        int index_max = 0;
        float highest = numbers[0];
        for (int index = 1; index < numbers.length; index ++) {
            if (numbers[index] > highest) {
                highest = numbers [index];
                index_max = index;
            }
        }
        return index_max;
    }

/*
    public class parsed_data {
        long timestamp_OnImageAvailable;
        int pitchDegree;
        int openpose_cnt;
        int yolo_cnt;
        float[][][] openpose_coord;
        float[][] yolo_coord;
        boolean bPerson = false;
    }
*/

    public void ParseServerReturn(String ServerReturns)
    {
        //clear old data
        openpose_coordinate.clear();
        yolo_coordinate.clear();

        String[] protobuf_result = ServerReturns.split(System.getProperty("line.separator"));

        for(String pr : protobuf_result){
            if(pr.contains("key")) {
                String[] key_split = pr.substring(6, pr.length() - 1).split("_");
                timestamp_OnImageAvailable = Long.parseLong(key_split[0]);
                pitchDegree = Integer.parseInt(key_split[1]);
            }
            else if(pr.contains("openpose_cnt")) {
                openpose_cnt = Integer.parseInt(pr.replace("openpose_cnt: ", ""));
            }
            else if(pr.contains("yolo_cnt")) {
                yolo_cnt = Integer.parseInt(pr.replace("yolo_cnt: ", ""));
            }
            else if(pr.contains("openpose_coord")) {
                String[] coord_str = pr.substring(17, pr.length() - 1).split(" \\\\n");
                float[][] coord_split = new float[18][3];
                for(int j = 0; j < 18; j++) {
                    String[] joint_coord_str = coord_str[j].split(" ");
                    for(int k = 0; k < 3; k++) {
                        coord_split[j][k] = Float.parseFloat(joint_coord_str[k]);
                    }
                }
                openpose_coordinate.add(coord_split);
            }
            else if(pr.contains("yolo_coord")) {
                String[] coord_str = pr.substring(13, pr.length() - 3).split(", ");
                float[] coord_split = new float[4];
                for(int j = 0; j < 4; j++)
                    coord_split[j] = Float.parseFloat(coord_str[j]);
                yolo_coordinate.add(coord_split);
            }
            else if(pr.contains("charades_webcam")) {
                actions = pr.substring(18, pr.length() - 1).split(";");
            }
        }
        //TODO: Is this criterion proper? Sometimes yolo_cnt = 0 but openpose_cnt > 0.
        bFoundPerson = (yolo_cnt > 0) && (openpose_cnt > 0);

        //Openpose sorts the order of skeletons by size. Thus it is ok to use the first returned skeleton.
        if(openpose_cnt > 0)
            fMatrix = openpose_coordinate.get(0);
        else
            fMatrix = new float[18][3];

        //TODO: Is the order of yolo returns sorted by size?
        if(yolo_cnt > 0)
            yMatrix = yolo_coordinate.get(0);
        else
            yMatrix = new float[4];


        //Check the probability, prevent the false positives.
        //If all probability values are less than the threshold, ignore the found person.
        float threshold = 0.4f;
        boolean bExceed = false;
        for(int i=0; i<18; i++)
        {
            if( fMatrix[i][2] > threshold) {
                bExceed = true;
                break;
            }
        }
        if( bExceed)
            bIgnorePerson = false;
        else
            bIgnorePerson =true;

        /*
        String[] lines = {"0", "120_134"};
        // Parse timestamp and pitch degree from 'key'

        String timestamp_and_pitchdegree = lines[lines.length-2];
        String[] timestamp_and_pitchdegree_splited = timestamp_and_pitchdegree.split("_");
        timestamp_OnImageAvailable = Long.parseLong(timestamp_and_pitchdegree_splited[0]);
        pitchDegree = Integer.parseInt(timestamp_and_pitchdegree_splited[1]);

        //parse actions
        String string_action = lines[lines.length-1];
        actions = string_action.split(";");

        if (lines.length > 3) {
            bFoundPerson = true;
            int number_of_people = (lines.length - 3)/19;
            int index_person_used = 0;
            if(number_of_people > 1) {
                float[] array_variance = new float[number_of_people];
                for (int index_person = 0; index_person < number_of_people; index_person++) {
                    String[] number_lines = Arrays.copyOfRange(lines, 1 + index_person * 19, (index_person + 1) * 19);
                    float[][] fMatrix = new float[18][3];
                    int i = 0, j;
                    for (String line : number_lines) {
                        j = 0;
                        String[] value_strings = line.split(" ");
                        for (String value_string : value_strings) {
                            fMatrix[i][j] = Float.parseFloat(value_string);
                            j++;
                        }
                        i++;
                    }
                    //compute the mean and variance
                    float sum_x = 0, sum_x_sq = 0, sum_y = 0, sum_y_sq = 0;
                    float mean_x, mean_y;
                    float amount = 0;
                    for (int idx = 0; idx < 18; idx++) {
                        if (fMatrix[idx][2] > 0) {
                            sum_x += fMatrix[idx][0];
                            sum_x_sq += fMatrix[idx][0] * fMatrix[idx][0];
                            sum_y += fMatrix[idx][1];
                            sum_y_sq += fMatrix[idx][1] * fMatrix[idx][1];
                            amount += 1f;
                        }
                    }
                    mean_x = sum_x / amount;
                    mean_y = sum_y / amount;
                    array_variance[index_person] = sum_x_sq - amount * mean_x * mean_x + sum_y_sq - amount * mean_y * mean_y;
                }
                index_person_used = find_max_value_in_numeric_array_with_java(array_variance);
            }
            //If there is only 1 person, index_person_used = 0
            String[] number_lines = Arrays.copyOfRange(lines, 1+index_person_used*19, (index_person_used+1)*19);
            int i = 0, j;
            for (String line : number_lines) {
                j = 0;
                String[] value_strings = line.split(" ");
                for (String value_string : value_strings) {
                    fMatrix[i][j] = Float.parseFloat(value_string);
                    j++;
                }
                i++;
            }

            //Check the probability, prevent the false positives.
            //If all probability values are less than the threshold, ignore the found person.
            float threshold = 0.4f;
            boolean bExceed = false;
            for(i=0; i<18; i++)
            {
                if( fMatrix[i][2] > threshold) {
                    bExceed = true;
                    break;
                }
            }
            if( bExceed)
                bIgnorePerson = false;
            else
                bIgnorePerson =true;
        }
        else
        {
            bFoundPerson = false;
            for(int i = 0; i<18; i++) {
                for (int j = 0; j < 3; j++) {
                    fMatrix[i][j] = 0;
                }
            }
        }
        */
    }
}
