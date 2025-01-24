package Bareket;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;

public class Tower extends Globals {

  private enum TOWER_STATE {
    saving,
    waitAfterSave,  //after saving is done, give a soldier a moment to use the money b4 we waste it
    normal,
  }

  //state specific
  static int savingTurns = 0;
  static int saveGoal = 0;
  static ArrayList<MapLocation> refillSpots = new ArrayList<>();
  static ArrayList<MapLocation> ruinSpots = new ArrayList<>();
  static ArrayList<MapLocation> ruinCleanSpots = new ArrayList<>();

  //upgrading
  static final int[] upgradeCost = {1000,2500,5000};
  private static boolean shouldSendMopperToCenter = false;

  static int[] unitsCreated = new int[3];
  static GAME_PHASE gamePhase = GAME_PHASE.early;

  private static TOWER_STATE state = TOWER_STATE.normal;

  // TODO: use smart weighted random for choosing units, or just not random at all, to avoid extreme cases of too little of a unit.
  public static void runTower(RobotController rc) throws GameActionException {

    mopperNotifyingCounter -= 1;
    determineIfNeedToSendMoppersToCenter(rc);


    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

    int rounds = rc.getRoundNum();
    if(rounds > EARLY_GAME_END) gamePhase = GAME_PHASE.mid;
    if(rounds > MID_GAME_END) gamePhase = GAME_PHASE.late;

    switch (state) {
      case TOWER_STATE.normal:

        // apply spawn pattern according to phase.
        switch(gamePhase) {
          case GAME_PHASE.early:  earlyGameSpawnPattern(rc, unitsCreated); break;
          case GAME_PHASE.mid: if(rc.getChips() > 1050) attemptCreatingUnits(rc); break;
          case GAME_PHASE.late: if(rc.getChips() > 1050) attemptCreatingUnits(rc); break;
        }

        break;
      //============
      case TOWER_STATE.waitAfterSave:
        savingTurns--;
        rc.setIndicatorString("waiting after save for " + savingTurns + "more turns");
        if (savingTurns <= 0) {
          state = TOWER_STATE.normal;
        }
        break;
      //============
      case TOWER_STATE.saving:

        rc.setIndicatorString("Saving until we reach " + saveGoal);
        if (rc.getChips() >= saveGoal) {
          state = TOWER_STATE.waitAfterSave;
          savingTurns = 2;
          rc.setIndicatorString("saving, aiming for: " + saveGoal);
        }
        break;

    }

    // === UPGRADE === //

    //if able*INSTANT_UPGARDE_MULTI, just do it.
    int level = rc.getType().level;
    if(level < 3 && rc.getChips() >= upgradeCost[level]*INSTANT_UPGARDE_MULTI && rc.canUpgradeTower(rc.getLocation()))// && gamePhase != GAME_PHASE.early)
    {
      rc.upgradeTower(rc.getLocation());
      System.out.println("upgrading");
    }


    // === ATTACK === //

    // TODO: change attacks
    for (RobotInfo robot : nearbyRobots) {
      if (rc.canAttack(robot.getLocation()) && rc.getTeam() != robot.getTeam()) {
        rc.attack(robot.getLocation());
      }
    }

    // === MESSAGES === //

    // Read incoming messages
    Message[] messages = rc.readMessages(-1);
    for (Message m : messages) {

      DecodedMessage<Object> message = new DecodedMessage<>(m.getBytes());
      System.out.println("Tower received message: #" + m.getSenderID() + " " + message);

      MESSAGE_TYPE type = message.type;
      switch (type) {
        case MESSAGE_TYPE.saveChips:
          saveGoal = (int)message.data;
          state = TOWER_STATE.saving;
          break;

        case MESSAGE_TYPE.askForRefill:
          refillSpots.add((MapLocation)message.data);
          break;

        case MESSAGE_TYPE.buildTowerHere:
          ruinSpots.add((MapLocation) message.data);
          break;

        case MESSAGE_TYPE.sendMopperToClearRuin:
          ruinCleanSpots.add((MapLocation) message.data);
          break;
      }

    }


    //Send messages
    //we wont send multipile missions to single robots, so the order of these indicate priority.
    //we can later maybe genelerize it using some list but for now it seems overkill.

    if (shouldSendMopperToCenter) {
      for (RobotInfo robot : nearbyRobots) {
        if (shouldSendMopperToCenter && robot.getTeam() == rc.getTeam()
            && robot.type == UnitType.MOPPER) {
          sendMopperToCenterOfMap(rc, robot);
        }
      }
    }

    //send robots to help build a ruin
    if(!ruinSpots.isEmpty()){
      for(RobotInfo robot : nearbyRobots){
        if(robot.getTeam() == rc.getTeam() && (double) robot.getPaintAmount() / robot.type.paintCapacity >= SOLDIER_PAINT_FOR_TASK
                && robot.type == UnitType.SOLDIER && !ruinSpots.isEmpty() && rc.canSendMessage(robot.getLocation())){
          //im not checking also whether i can send him a message cuz im pretty sure "sense <=> can send" but look out for exceptions.
          int msg = Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere,ruinSpots.removeFirst());
          rc.sendMessage(robot.getLocation(),msg);
        }
      }
    }
    //send moppers to clean ruins
    if (!ruinCleanSpots.isEmpty()) {
      for (RobotInfo robot : nearbyRobots) {
        if (robot.getTeam() == rc.getTeam() && robot.getType() == UnitType.MOPPER && !ruinCleanSpots.isEmpty()
            && rc.canSendMessage(robot.getLocation())) {
          int msg = Utils.encodeMessage(MESSAGE_TYPE.sendMopperToClearRuin, ruinCleanSpots.removeFirst());
          rc.sendMessage(robot.getLocation(), msg);

        }
      }

    rc.setIndicatorString(Arrays.toString(unitsCreated));}
      
  }


  /******************************************** METHODS ******************************************/

  private static void attemptCreatingUnits(RobotController rc) throws GameActionException {
    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLocation = rc.getLocation().add(dir);
    int robotType = rng.nextInt(7);

    if (robotType <= 3 && rc.canBuildRobot(EARLY_GAME_MAIN_UNIT, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
      rc.buildRobot(EARLY_GAME_MAIN_UNIT, nextLocation);
      System.out.println("BUILT A " + EARLY_GAME_MAIN_UNIT.name());
      unitsCreated[EARLY_GAME_MAIN_UNIT.ordinal()]++;
    }

    else if (robotType == 4 && rc.canBuildRobot(EARLY_GAME_LAST_UNIT, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
      rc.buildRobot(EARLY_GAME_LAST_UNIT, nextLocation);
      System.out.println("BUILT A " + EARLY_GAME_LAST_UNIT.name());
      unitsCreated[EARLY_GAME_LAST_UNIT.ordinal()]++;
    }

    else if (robotType >= 5 && rc.canBuildRobot(EARLY_GAME_SECONDARY_UNIT, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
      rc.buildRobot(EARLY_GAME_SECONDARY_UNIT, nextLocation);
      System.out.println("BUILT A " + EARLY_GAME_SECONDARY_UNIT.name());
      unitsCreated[EARLY_GAME_SECONDARY_UNIT.ordinal()]++;
    }
  }

  private static void attemptCreatingUnits(RobotController rc, UnitType type) throws GameActionException {
    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLocation = rc.getLocation().add(dir);

    if (rc.canBuildRobot(type, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
      rc.buildRobot(type, nextLocation);
      System.out.println("BUILT A " + type.name());
      unitsCreated[type.ordinal()]++;
    }

  }

  private static void determineIfNeedToSendMoppersToCenter(RobotController rc){
    MapLocation location = rc.getLocation();
    mapWidth = rc.getMapWidth();
    mapHeight = rc.getMapHeight();
    mapCenter = new MapLocation(mapWidth / 2, mapHeight / 2);

    float divide = Math.abs((float) location.x / (float) mapWidth);

    // if in map edges send mopper to fight boom boom
    if (divide <= Math.abs(0.25) || divide >= Math.abs(0.75)) {
      shouldSendMopperToCenter = true;
    }

  }

  private static void sendMopperToCenterOfMap(RobotController rc, RobotInfo robot) throws GameActionException{
        int plusOrMinus = (int) (Math.random() * 10) > 4 ? 1 : -1;
        MapLocation adjustedLocation = new MapLocation(mapCenter.x + (int) (Math.random() * 5 * plusOrMinus),
            (int) (mapHeight * (Math.random())));
        int msg = Utils.encodeMessage(MESSAGE_TYPE.sendMopperToCenterOfMap, adjustedLocation);
        if (rc.canSendMessage(robot.getLocation())) {
          rc.setIndicatorString("SENDING GET TO CENTER: " + adjustedLocation);
          rc.sendMessage(robot.getLocation(), msg);
          mopperNotifyingCounter = 30;
        }

  }

  private static void earlyGameSpawnPattern(RobotController rc, int[] spawnsCount) throws GameActionException {

    //find spawnset according to my type
    int[] spawnset = EARLY_PAINT_SPAWNS;
    switch(rc.getType()){
      case UnitType.LEVEL_ONE_PAINT_TOWER:
      case UnitType.LEVEL_TWO_PAINT_TOWER:
      case UnitType.LEVEL_THREE_PAINT_TOWER:
        spawnset = EARLY_PAINT_SPAWNS;
        break;
      case UnitType.LEVEL_ONE_MONEY_TOWER:
      case UnitType.LEVEL_TWO_MONEY_TOWER:
      case UnitType.LEVEL_THREE_MONEY_TOWER:
        spawnset = EARLY_MONEY_SPAWNS;
        break;
      default:
        spawnset = EARLY_DEFENSE_SPAWNS;
    }

    //spawn units if need more
    if(spawnsCount[UnitType.SOLDIER.ordinal()] < spawnset[UnitType.SOLDIER.ordinal()])    attemptCreatingUnits(rc,UnitType.SOLDIER);
    if(spawnsCount[UnitType.MOPPER.ordinal()]  < spawnset[UnitType.MOPPER.ordinal()])     attemptCreatingUnits(rc,UnitType.MOPPER);
    if(spawnsCount[UnitType.SPLASHER.ordinal()] < spawnset[UnitType.SPLASHER.ordinal()])  attemptCreatingUnits(rc,UnitType.SPLASHER);

    //spawn extra
    if(rc.getChips() >= EARLY_CHIPS_THRESHOLD && rc.getPaint() >= EARLY_PAINT_THRESHOLD){
      attemptCreatingUnits(rc);
    }
  }
}
