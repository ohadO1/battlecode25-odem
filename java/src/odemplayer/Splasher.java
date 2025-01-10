package odemplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Splasher extends Globals {

  public static void runSplasher(RobotController rc) throws GameActionException {
    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLoc = Utils.roamGracefullyf(rc);

    if (rc.canMove(dir)) {
      rc.move(dir);
    }
    if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }
  }

}
