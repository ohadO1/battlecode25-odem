package odemplayer;

import java.util.ArrayList;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Globals {

  /********** Unit Names *********/

  public static UnitType[] PAINT_TOWER_NAMES = { UnitType.LEVEL_THREE_PAINT_TOWER, UnitType.LEVEL_TWO_PAINT_TOWER,
      UnitType.LEVEL_THREE_PAINT_TOWER };

  public static UnitType[] MONEY_TOWER_NAMES = { UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_TWO_MONEY_TOWER,
      UnitType.LEVEL_THREE_MONEY_TOWER };

  public static UnitType[] DEFENSE_TOWER_NAMES = { UnitType.LEVEL_TWO_DEFENSE_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER,
      UnitType.LEVEL_THREE_PAINT_TOWER };

  /********************************/

  public enum MESSAGE_TYPE {
    saveChips,
    askForRefill,
    buildTowerHere,
    attackTower,
  }

  // in the encoder/decoder, use: messageTypesIndexes.indexOf(type) to get a
  // consistent int. (its an array but you get it)
  public static MESSAGE_TYPE[] messageTypesIndexes = {
      MESSAGE_TYPE.saveChips,
      MESSAGE_TYPE.askForRefill,
      MESSAGE_TYPE.buildTowerHere,
      MESSAGE_TYPE.attackTower,
  };

  // TODO: some of the globals should be more specific (tower or unit scope)
  // TODO: get width and height of the map for further calculations
  //
  static int turnCount = 0;

  public enum GAME_PHASE {
    early,
    mid,
    late,
  }

  // messanger:
  static boolean isMessanger = false;

  static ArrayList<RobotInfo> knownTowersInfos = new ArrayList<>();


  static final Random rng = new Random();

  static final Direction[] directions = {
      Direction.NORTH,
      Direction.NORTHEAST,
      Direction.EAST,
      Direction.SOUTHEAST,
      Direction.SOUTH,
      Direction.SOUTHWEST,
      Direction.WEST,
      Direction.NORTHWEST,
  };

  /********************** STARTEGY **************************/

 
  static final UnitType DEFUALT_TOWER_TO_BUILD = UnitType.LEVEL_ONE_PAINT_TOWER;
  static final UnitType EARLY_GAME_MAIN_UNIT = UnitType.SOLDIER;
  static final UnitType EARLY_GAME_SECONDARY_UNIT = UnitType.SOLDIER;
  static final UnitType EARLY_GAME_LAST_UNIT = UnitType.SPLASHER;
  static final double SOLDIER_PAINT_FOR_TASK = 0.4; // when a soldier fails to refill paint but has above this, he will
  static final int PAINT_TOWER_SAVING_TURNS = 50;

}
