package odemplayer;

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

    switch (role) {
      case messenger:
        if (state == MOPPER_STATE.roam && knownTowersInfos.size() > 0) {
          MapLocation destination = Utils.findClosestTower(knownTowersInfos, rc);
          PathFinder.moveToLocation(rc, destination);
          Utils.updateFriendlyTowers(rc);
        }
        // NOTE: for debugging, remove when submitting
        rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);

        checkNearbyRuins(rc);
        // tbd

      default:
    }

    // NOTE: this code will execute on every role assigned to the unit. this code
    // needs to improve, no logic involved in roaming or attacking
    MapLocation nextLoc = Utils.roamGracefullyf(rc);

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

  public static void checkNearbyRuins(RobotController rc) throws GameActionException {
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

      // check if there is a ruin but there is no robot on top of the ruin (tower)
      state = MOPPER_STATE.saving;
      // isSaving = true;
    }
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
