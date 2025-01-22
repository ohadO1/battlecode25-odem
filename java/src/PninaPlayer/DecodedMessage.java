package PninaPlayer;

import battlecode.common.MapLocation;
import org.hibernate.mapping.Any;

import java.util.ArrayList;

public class DecodedMessage<T> extends Globals{

    MESSAGE_TYPE type;

    //i forgot how to use generic types, i cant make it work here.
    //we'll just have a bunch of
    T data;

    public DecodedMessage(int message){

        int currentData = message%10;
        message /= 10;

        type = MESSAGE_TYPE.values()[currentData];

        switch(type){
            case MESSAGE_TYPE.buildTowerHere:
            case MESSAGE_TYPE.sendMopperToClearRuin:
            case MESSAGE_TYPE.askForRefill:

                int x = message % 100;
                message /= 100;
                int y = message % 100;

                data = ((T)new MapLocation(x,y));
                break;
            case MESSAGE_TYPE.saveChips:
                data = ((T)Integer.valueOf(message));
                break;
        }

//        System.out.println("decoded message: [" + type.name() + ", " + data + "]");
    }
    public String toString(){
        return type.name() + ": " + data;
    }
}
