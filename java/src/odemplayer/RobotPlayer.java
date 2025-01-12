package odemplayer;

import battlecode.common.*;

public class RobotPlayer extends Globals {

  // TODO: handle attacking enemy units
  public static void run(RobotController rc) throws GameActionException {
    if (rc.getType() == UnitType.MOPPER && rc.getID() % 2 == 0) {
      // if a mopper is a messanger
      unitRole = UNIT_ROLES.messenger;
    }

    while (true) {
      turnCount++;

      try {
        switch (rc.getType()) {
          case SOLDIER:
            Soldier.runSoldier(rc);
            break;
          case MOPPER:
            Mopper.runMopper(rc);
            break;
          case SPLASHER:
            Splasher.runSplasher(rc);
            break;

          default:
            Tower.runTower(rc);
            break;
        }

      } catch (GameActionException e) {
        System.out.println("GAME ACTION EXCEPTION");
        e.printStackTrace();
      } finally {
        Clock.yield();
      }

    }
  }
}
