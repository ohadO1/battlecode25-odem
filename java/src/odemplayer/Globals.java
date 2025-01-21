package odemplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
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

  /********************** UTILITY VARIABLES *****************/

  static int circleRoamRadius = 0;
  static int circleRoamUpdate = -100;
  static int circleRoamAngle = 0;
  static MapLocation circleRoamCenter = new MapLocation(0,0);
  static MapLocation circleRoamDest = new MapLocation(0,0);

  /********************** STARTEGY **************************/


  /********* general *********/
  static final UnitType DEFUALT_TOWER_TO_BUILD = UnitType.LEVEL_ONE_PAINT_TOWER;
  static final int EARLY_GAME_END = 100;
  static final int MID_GAME_END = 200;
  static final int LATE_GAME_END = 300;

  //units will atempt to build towers in this order. will be affected by the unit's knownTowersInfos.
  static ArrayList<UnitType> idealTowerOrder = new ArrayList<>(Arrays.asList(
      UnitType.LEVEL_ONE_PAINT_TOWER, //default
      UnitType.LEVEL_ONE_MONEY_TOWER, //default
      UnitType.LEVEL_ONE_PAINT_TOWER,
      UnitType.LEVEL_ONE_MONEY_TOWER,
      UnitType.LEVEL_ONE_DEFENSE_TOWER,
      UnitType.LEVEL_ONE_PAINT_TOWER,
      UnitType.LEVEL_ONE_MONEY_TOWER,
      UnitType.LEVEL_ONE_DEFENSE_TOWER,
      UnitType.LEVEL_ONE_PAINT_TOWER,
      UnitType.LEVEL_ONE_MONEY_TOWER,
      UnitType.LEVEL_ONE_PAINT_TOWER,
      UnitType.LEVEL_ONE_MONEY_TOWER,
      UnitType.LEVEL_ONE_DEFENSE_TOWER
  ));
  //units will remove values here when they notice towers, and pick the first one as their priority.

  /*** towers ***/
  static final UnitType EARLY_GAME_MAIN_UNIT = UnitType.SOLDIER;
  static final UnitType EARLY_GAME_SECONDARY_UNIT = UnitType.MOPPER;
  static final UnitType EARLY_GAME_LAST_UNIT = UnitType.SPLASHER;
  static final int EARLY_CHIPS_THRESHOLD = 1200;
  static final int EARLY_PAINT_THRESHOLD = 300;

  // at the start of the game, towers will only spawn these units,
  // and spawn more only if they have over EARLY_CHIPS_THRESHOLD.
  static final int[] EARLY_PAINT_SPAWNS = {
          2,  //soldier
          0,  //splasher
          0,  //mopper
  };
  static final int[] EARLY_MONEY_SPAWNS = {
          1,  //soldier
          0,  //splasher
          1,  //mopper
  };

  /*** soldier ***/
  static final double SOLDIER_PAINT_FOR_TASK = 0.4;                     // when a soldier fails to refill paint but has above this, he will
  static final double SOLDIER_PAINT_FOR_CASUAL_REFILL = 0.4;                   // when a soldier has under this % and doesnt do anything special hell seek refill.
  static final double SOLDIER_PAINT_FOR_URGENT_REFILL = 0.25;                   // when a soldier has under this % he will stop whatever hes doing and seek refill.
  static final int ALLIES_FOR_ATTACK_EARLY = 2; //init an attack only where there are this many friendly soldiers\splashers around, in early game.
  static final int ALLIES_FOR_ATTACK_MID   = 3; //init an attack only where there are this many friendly soldiers\splashers around, in mid game.
  static final int ALLIES_FOR_ATTACK_LATE = 4;  //init an attack only where there are this many friendly soldiers\splashers around, in late game.

  /*** utility ***/
  static final int CIRCLE_ROAM_ROUNDS_TO_RESET = 15;    //circle roam will reset its radius if not used after this amount of turns
  static final int CIRCLE_ROAM_MAX_RADIUS = 30;
}
