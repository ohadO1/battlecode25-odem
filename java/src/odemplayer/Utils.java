package odemplayer;

import java.util.ArrayList;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

//NOTE: document well each function, will help us in later stages of the project
//
public class Utils extends Globals {

  // TODO: not efficient enough! fix
  /**
   * method that avoids walls when roaming (no task yet)
   * 
   * @param rc - RobotController
   * @return next location - MapLocation
   */
  public static MapLocation roamGracefullyf(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(2);
    for (MapInfo tile : nearbyTiles) {
      if (tile.isWall()) {
        MapLocation wallLocation = tile.getMapLocation();
        Direction dir = rc.getLocation().directionTo(wallLocation).opposite();
        if (rc.canMove(dir)) {
          // NOTE: for testing purposes, remove after finished
          rc.setIndicatorDot(rc.getLocation(), 0, 0, 255);
          rc.move(dir);
          return rc.getLocation().add(dir);
        }
        return null;
      }
    }

    Direction dir = directions[rng.nextInt(directions.length)];

    if (rc.canMove(dir)) {
      rc.move(dir);
    }

    return rc.getLocation().add(dir);
  }

  /**
   * finds the closest tower (out of the known tower locations)
   * 
   * @param rc                   - RobotController
   * @param knownTowersLocations - ArrayList<MapLocation>
   * @return closestTowerLocation - MapLocation
   */
  public static MapLocation findClosestTower(ArrayList<MapLocation> knownTowersLocations, RobotController rc) {
    int distance = 99999;
    MapLocation closestLocation = null;

    for (MapLocation location : knownTowersLocations) {
      int foundDistance = location.distanceSquaredTo(rc.getLocation());
      if (distance > foundDistance) {
        distance = foundDistance;
        closestLocation = location;
        continue;
      }
    }
    return closestLocation;
  }

}
