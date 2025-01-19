package odemplayer;

import battlecode.common.*;

import java.util.ArrayList;

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

  private static TOWER_STATE state = TOWER_STATE.normal;

  // TODO: dont create units all the time.
  // TODO: use current phase
  // TODO: use smart weighted random for choosing units, or just not random at all, to avoid extreme cases of too little of a unit.
  // TODO: send refill message to moppers
  // TODO: alert units to help build a tower
  public static void runTower(RobotController rc) throws GameActionException {

    GAME_PHASE current_phase = GAME_PHASE.early;  //shouldnt this be a static?
    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

    switch (state) {
      case TOWER_STATE.normal:

        // build more soldiers
        attemptCreatingUnits(rc);

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
        }
        break;

    }

    // === ATTACK === //

    // TODO: change attacks
//    RobotInfo[] nearbyRobots = rc.senseNearbyRobots(); moved to top
    for (RobotInfo robot : nearbyRobots) {
      if (rc.canAttack(robot.getLocation()) && rc.getTeam() != robot.getTeam()) {
        rc.attack(robot.getLocation());
      }
    }

    // === MESSAGES === //

    // Read incoming messages
    // TODO: move to messages module
    Message[] messages = rc.readMessages(-1);
    for (Message m : messages) {

      DecodedMessage<Object> message = new DecodedMessage<>(m.getBytes());
      System.out.println("Tower received message: '#" + m.getSenderID() + " " + message);

      MESSAGE_TYPE type = message.type;
      switch (type) {
        case MESSAGE_TYPE.saveChips:
          saveGoal = (int)message.data;
//          savingTurns = 50;
          state = TOWER_STATE.saving;
          break;

        case MESSAGE_TYPE.askForRefill:
          refillSpots.add((MapLocation)message.data);
          break;

        case MESSAGE_TYPE.buildTowerHere:
          ruinSpots.add((MapLocation) message.data);
          break;
      }
    }


    //Send messages
    //we wont send multipile missions to single robots, so the order of these indicate priority.
    //we can later maybe genelerize it using some list but for now it seems overkill.

    //TODO merge the loops
    //send robots to help build a ruin
    if(!ruinSpots.isEmpty()){
      //sending one extra robot seems enough.
      //if it turns out to not be the case, add some logic for remebering how many we sent for the current spot.
      for(RobotInfo robot : nearbyRobots){
        if(robot.getTeam() == rc.getTeam() && (double) robot.getPaintAmount() / robot.type.paintCapacity >= SOLDIER_PAINT_FOR_TASK
                && robot.type == UnitType.SOLDIER){
          //im not checking also whether i can send him a message cuz im pretty sure "sense <=> can send" but look out for exceptions.
          int msg = Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere,ruinSpots.removeFirst());
          rc.sendMessage(robot.getLocation(),msg);
        }
      }
    }
    //send moppers to refill soldiers and splashers
    if(!refillSpots.isEmpty()){
      for(RobotInfo robot : nearbyRobots){
        if(robot.getTeam() == rc.getTeam() && robot.type == UnitType.MOPPER){
          int msg = Utils.encodeMessage(MESSAGE_TYPE.askForRefill,refillSpots.removeFirst());
          rc.sendMessage(robot.getLocation(),msg);
        }
      }
    }

  }
  //============

  private static void attemptCreatingUnits(RobotController rc) throws GameActionException {
      Direction dir = directions[rng.nextInt(directions.length)];
      MapLocation nextLocation = rc.getLocation().add(dir);
      int robotType = rng.nextInt(4);

      if (robotType <= 1 && rc.canBuildRobot(EARLY_GAME_MAIN_UNIT, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
        rc.buildRobot(EARLY_GAME_MAIN_UNIT, nextLocation);
        System.out.println("BUILT A SOLDIER");
      }

      else if (robotType == 2 && rc.canBuildRobot(EARLY_GAME_LAST_UNIT, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
        rc.buildRobot(EARLY_GAME_LAST_UNIT, nextLocation);
        System.out.println("BUILT A SPLASHER");
      }

      else if (robotType == 3 && rc.canBuildRobot(EARLY_GAME_SECONDARY_UNIT, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
        rc.buildRobot(EARLY_GAME_SECONDARY_UNIT, nextLocation);
        System.out.println("BUILT A MOPPER");
      }
  }
}
