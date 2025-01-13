package odemplayer;

import battlecode.common.MapLocation;

import java.util.ArrayList;

public class DecodedMessage<T> extends Globals{

    MESSAGE_TYPE type;
    ArrayList<?> data;

    public DecodedMessage(int message){

        int currentData = message%10;
        message /= 10;

        type = MESSAGE_TYPE.values()[currentData];

        switch(type){
            case MESSAGE_TYPE.buildTowerHere:
            case MESSAGE_TYPE.askForRefill:
//                data.add();
                break;
        }
    }
}
