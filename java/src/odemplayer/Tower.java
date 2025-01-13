package odemplayer;

import battlecode.common.*;

public class Tower extends Globals {

  // TODO: too long. improve
  public static void runTower(RobotController rc) throws GameActionException {

    GAME_PHASE current_phase = GAME_PHASE.early;

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
      if (robotType <= 1 && rc.canBuildRobot(EarlyGameMainUnit, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
        rc.buildRobot(EarlyGameMainUnit, nextLocation);
        System.out.println("BUILT A SOLDIER");
      }

      else if (robotType == 2 && rc.canBuildRobot(EarlyGameLastUnit, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
        rc.buildRobot(EarlyGameLastUnit, nextLocation);
        System.out.println("BUILT A SPLASHER");
      }

      else if (robotType == 3 && rc.canBuildRobot(EarlyGameSecondarySecondaryUnit, nextLocation) && rc.senseRobotAtLocation(nextLocation) == null) {
        rc.buildRobot(EarlyGameSecondarySecondaryUnit, nextLocation);
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

      if (m.getBytes() == MESSAGE_TYPE.saveChips.ordinal() && !isSaving) {
        // TODO: Make more specific
        savingTurns = 50;
        isSaving = true;
      }
    }
  }

}
