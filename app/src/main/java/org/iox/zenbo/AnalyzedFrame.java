package org.iox.zenbo;

import java.util.Arrays;

public class AnalyzedFrame {
    long timestamp_OnImageAvailable;
    long timestamp_ReceivedFromServer;
    int pitchDegree;
    boolean bNew = false;
    float[][] fMatrix;
    boolean bFoundPerson = false;
    boolean bIgnorePerson = false;
    boolean bAvailable = false;
    String[] actions;

    public AnalyzedFrame()
    {
        fMatrix = new float[18][3];
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

    public void ParseServerReturn(String ServerReturns)
    {
        String[] lines = ServerReturns.split(System.getProperty("line.separator"));
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

    }
}
