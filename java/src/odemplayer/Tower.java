package odemplayer;

import battlecode.common.*;

public class Tower extends Globals {

  // TODO: too long. improve
  public static void runTower(RobotController rc) throws GameActionException {

    GAME_PHASAE current_phase = GAME_PHASAE.early;

    if (savingTurns == 0) {
      isSaving = false;

      // upgrade if able
      // if (rc.canUpgradeTower(rc.getLocation())) {
      // rc.upgradeTower(rc.getLocation());
      // }

      Direction dir = directions[rng.nextInt(directions.length)];
      MapLocation nextLocation = rc.getLocation().add(dir);
      int robotType = rng.nextInt(4);

      // build more soldiers
      if (robotType == 0 || robotType == 1 && rc.canBuildRobot(UnitType.SOLDIER, nextLocation)) {
        rc.buildRobot(UnitType.SOLDIER, nextLocation);
        System.out.println("BUILT A SOLDIER");
      }

      else if (robotType == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLocation)) {
        rc.buildRobot(UnitType.SPLASHER, nextLocation);
        System.out.println("BUILT A SPLASHER");
      }

      else if (robotType == 3 && rc.canBuildRobot(UnitType.MOPPER, nextLocation)) {
        rc.buildRobot(UnitType.MOPPER, nextLocation);
        System.out.println("BUILT A MOPPER");
      }
    } else {
      savingTurns--;
      rc.setIndicatorString("Saving For " + savingTurns + "More Turns");
    }

    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
    for (RobotInfo robot : nearbyRobots) {
      if (rc.canAttack(robot.getLocation())) {
        rc.attack(robot.getLocation());
      }
    }

    // Read incoming messages
    Message[] messages = rc.readMessages(-1);
    for (Message m : messages) {
      System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());

      if (m.getBytes() == MessageType.SAVE_CHIPS.ordinal() && !isSaving) {
        // TODO: Make more specific
        savingTurns = 50;
        isSaving = true;
      }
    }
  }

}
