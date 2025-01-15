package odemplayer;

import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends Globals {

  private enum MOPPER_ROLES {
    messenger,
    refiller,
    normal
  }

  private enum MOPPER_STATE {
    transferPaint,
    notifyTower,
    roam,
    saving
  }

  private static MOPPER_ROLES role = MOPPER_ROLES.normal;

  private static MOPPER_STATE state = MOPPER_STATE.roam;

  public static void runMopper(RobotController rc) throws GameActionException {

    // TODO: add a role that will support soldiers and refill them from tower
    // contantly
    //
    switch (state) {
      case roam:
        Utils.roamGracefullyf(rc);
        // find out more about attcks
        // mopperAttack(rc, nextLoc);
        if (checkNearbyRuins(rc) && knownTowersInfos.size() > 0) {
          state = MOPPER_STATE.notifyTower;
          MapLocation destination = Utils.findClosestTower(knownTowersInfos, rc);
          PathFinder.moveToLocation(rc, destination);
        }
        break;
      // case messenger:
      case saving:
        MapLocation destination = Utils.findClosestTower(knownTowersInfos, rc);
        PathFinder.moveToLocation(rc, destination);
        boolean didUpdateTowerToSave = Utils.updateFriendlyTowers(rc);
        // // NOTE: for debugging, remove when submitting
        rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
        if (didUpdateTowerToSave) {
          state = MOPPER_STATE.notifyTower;
        }

      default:
    }
  }

  // TODO: change it
  public static void mopperAttack(RobotController rc, MapLocation nextLoc) throws GameActionException {
    // how do we attack?
    for (Direction tryMopDirection : directions) {
      if (rc.canMopSwing(tryMopDirection)) {
        rc.mopSwing(tryMopDirection);
      }
    }

    if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }

  }

  public static boolean checkNearbyRuins(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

    for (MapInfo tile : nearbyTiles) {

      if (!tile.hasRuin())
        continue;

      if (rc.senseRobotAtLocation(tile.getMapLocation()) != null) {
        continue;
      }

      Direction dir = tile.getMapLocation().directionTo(rc.getLocation());
      MapLocation markTile = tile.getMapLocation().add(dir);

      if (!rc.senseMapInfo(markTile).getMark().isAlly()) {
        continue;
      }

      // found not occupied ruin
      return true;
    }
    return false;
  }

  public static void determineMopperRole(RobotController rc) {
    int id = rc.getID();
    switch (id % 2) {
      case 0:
        Mopper.role = MOPPER_ROLES.messenger;
      default:
        Mopper.role = MOPPER_ROLES.normal;
    }
  }

  /**
   * @param - how much paint to take or give to the unit (positive to give,
   *          negative to take)
   * @param - targetLocation
   * @return void
   */
  public static void tranferPaintToLocation(RobotController rc,
      MapLocation targetLocation, int amount) throws GameActionException {

    if (rc.canTransferPaint(targetLocation, amount)) {
      rc.transferPaint(targetLocation, amount);
      return;
    }

    PathFinder.moveToLocation(rc, targetLocation);
  }

}
