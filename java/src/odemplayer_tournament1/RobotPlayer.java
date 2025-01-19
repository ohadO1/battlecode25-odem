package odemplayer_tournament1;

import battlecode.common.*;

public class RobotPlayer extends Globals {

  // TODO: handle attacking enemy units
  public static void run(RobotController rc) throws GameActionException {
    if (rc.getType() == UnitType.MOPPER && rc.getID() % 2 == 0) {
      System.out.println(rc.getID());
      // if a mopper is a messanger
      isMessanger = true;
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
