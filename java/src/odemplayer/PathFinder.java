
package odemplayer;

import java.util.HashSet;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class PathFinder extends Globals {

  static private boolean isTracing = false;
  static private Direction tracingDir;
  static private HashSet<MapLocation> line = null;
  static private MapLocation prevDest = null;
  static int obstacleStartDist;

  /**
   * helper function - Bresenham's line algorithm
   * 
   * @param a source unit maplocation - MapLocation
   * @param b target unit maplocation - MapLocation
   */
  public static HashSet<MapLocation> createLine(MapLocation a, MapLocation b) {
    HashSet<MapLocation> locs = new HashSet<>();
    int x = a.x, y = a.y;
    int dx = b.x - a.x;
    int dy = b.y - a.y;
    int sx = (int) Math.signum(dx);
    int sy = (int) Math.signum(dy);
    dx = Math.abs(dx);
    dy = Math.abs(dy);
    int d = Math.max(dx, dy);
    int r = d / 2;
    if (dx > dy) {
      for (int i = 0; i < d; i++) {
        locs.add(new MapLocation(x, y));
        x += sx;
        r += dy;
        if (r >= dx) {
          locs.add(new MapLocation(x, y));
          y += sy;
          r -= dx;
        }
      }
    } else {
      for (int i = 0; i < d; i++) {
        locs.add(new MapLocation(x, y));
        y += sy;
        r += dx;
        if (r >= dy) {
          locs.add(new MapLocation(x, y));
          x += sx;
          r -= dy;
        }
      }
    }
    locs.add(new MapLocation(x, y));
    return locs;
  }

  /**
   * moves to location even when facing walls
   * 
   * @param rc     - RobotController
   * @param target - MapLocation (target location)
   * @returns - void
   */

  public static void moveToLocation(RobotController rc, MapLocation target) throws GameActionException {

    if (rc.getLocation().distanceSquaredTo(target) <= 2) {
      Direction targetDir = rc.getLocation().directionTo(target);
      if (!rc.canMove(targetDir)) {
        return;
      }
    }

    if (!target.equals(prevDest)) {
      prevDest = target;
      line = createLine(rc.getLocation(), target);
    }

    for (MapLocation loc : line) {
      rc.setIndicatorDot(loc, 255, 0, 0);
    }

    if (!isTracing) {
      Direction dir = rc.getLocation().directionTo(target);
      rc.setIndicatorDot(rc.getLocation().add(dir), 255, 0, 0);
      Clock.yield();

      if (rc.canMove(dir)) {
        rc.move(dir);
      } else {
        isTracing = true;
        obstacleStartDist = rc.getLocation().distanceSquaredTo(target);
        tracingDir = dir;
      }
    } else {
      if (line.contains(rc.getLocation()) && rc.getLocation().distanceSquaredTo(target) < obstacleStartDist) {
        isTracing = false;
      }

      for (int i = 0; i < 9; i++) {
        if (rc.canMove(tracingDir)) {
          rc.move(tracingDir);
          tracingDir = tracingDir.rotateRight();
          tracingDir = tracingDir.rotateRight();
          break;
        } else {
          tracingDir = tracingDir.rotateLeft();
        }
      }
    }
  }

}
