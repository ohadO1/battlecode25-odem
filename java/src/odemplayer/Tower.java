package odemplayer;

import battlecode.common.*;

public class Tower extends Globals {

  private enum TOWER_STATE {
    saving,
    normal
  }

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
        if (robotType == 0 || robotType == 1 && rc.canBuildRobot(UnitType.SOLDIER, nextLocation)) {
          rc.buildRobot(EARLY_GAME_MAIN_UNIT, nextLocation);
          System.out.println("BUILT A SOLDIER");
        }

        else if (robotType == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLocation)) {
          rc.buildRobot(UnitType.SPLASHER, nextLocation);
          System.out.println("BUILT A SPLASHER");
        }

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
      case TOWER_STATE.saving: {
        savingTurns--;
        rc.setIndicatorString("Saving For " + savingTurns + "More Turns");
      }
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
      System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());

      if (m.getBytes() == MESSAGE_TYPE.saveChips.ordinal() && state != TOWER_STATE.saving) {
        // TODO: Make more specific
        savingTurns = 50;
        state = TOWER_STATE.saving;
      }
    }
  }

}
