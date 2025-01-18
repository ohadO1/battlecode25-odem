package odemplayer;

import battlecode.common.*;

public class Tower extends Globals {

  private enum TOWER_STATE {
    saving,
    normal
  }

  // tower
  static int savingTurns = 0;
  //

  private static TOWER_STATE state = TOWER_STATE.normal;

  // TODO: too long. improve
  public static void runTower(RobotController rc) throws GameActionException {

    GAME_PHASE current_phase = GAME_PHASE.early;

    switch (state) {
      case TOWER_STATE.normal:
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLocation = rc.getLocation().add(dir);
        int robotType = rng.nextInt(4);

        // build more soldiers
        if (robotType <= 1 && rc.canBuildRobot(EARLY_GAME_MAIN_UNIT, nextLocation)
            && rc.senseRobotAtLocation(nextLocation) == null) {
          rc.buildRobot(EARLY_GAME_MAIN_UNIT, nextLocation);
          System.out.println("BUILT A SOLDIER");
        }

        else if (robotType == 2 && rc.canBuildRobot(EARLY_GAME_LAST_UNIT, nextLocation)
            && rc.senseRobotAtLocation(nextLocation) == null) {
          rc.buildRobot(EARLY_GAME_LAST_UNIT, nextLocation);
          System.out.println("BUILT A SPLASHER");
        }

        else if (robotType == 3 && rc.canBuildRobot(EARLY_GAME_SECONDARY_UNIT, nextLocation)
            && rc.senseRobotAtLocation(nextLocation) == null) {
          rc.buildRobot(EARLY_GAME_SECONDARY_UNIT, nextLocation);
          System.out.println("BUILT A MOPPER");
        }
        break;
      case TOWER_STATE.saving:
        savingTurns--;
        rc.setIndicatorString("Saving For " + savingTurns + "More Turns");
        if (savingTurns <= 0) {
          state = TOWER_STATE.normal;
        }
        break;

    }

    // TODO: change attacks
    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
    for (RobotInfo robot : nearbyRobots) {
      if (rc.canAttack(robot.getLocation())) {
        rc.attack(robot.getLocation());
      }
    }

    // Read incoming messages
    // TODO: move to messages module
    Message[] messages = rc.readMessages(-1);
    for (Message m : messages) {
      System.out.println("Tower received message: '#" + m.getSenderID() + " " +
          m.getBytes());

      DecodedMessage message = new DecodedMessage<>(m.getBytes());
      MESSAGE_TYPE type = message.type;

      //fix saving bug
      switch (type) {
        case MESSAGE_TYPE.saveChips:
          // TODO: Make more specific
          int saveGoal = (int)message.data;
          // impelement save goal
          savingTurns = 50;
          state = TOWER_STATE.saving;
          break;
      }
    }
  }
}
