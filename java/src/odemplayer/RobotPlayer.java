package odemplayer;

import battlecode.common.*;
public class RobotPlayer extends Globals {

  // TODO: handle attacking enemy units
  public static void run(RobotController rc) throws GameActionException {

    UnitType unitType = rc.getType();

    // initializers. will execute in the beginning of the method. use to set roles,
    // etc
    switch (unitType) {
      case MOPPER:
        Mopper.determineMopperRole(rc);
    }

    while (true) {
      turnCount++;
      try {
        switch (unitType) {
          case SOLDIER:
            Soldier.runSoldier(rc);
            break;
          case MOPPER:
            Mopper.runMopper(rc);
            break;
          case SPLASHER:
            Splasher.runSplasher(rc);
            break;

          // tower might be too generic, split code by kind of tower
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
