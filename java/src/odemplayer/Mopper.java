package odemplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends Globals {

  public enum MOPPER_ROLES {
    messenger,
    normal
  }

  static MOPPER_ROLES role = MOPPER_ROLES.normal;

  public static void runMopper(RobotController rc) throws GameActionException {
    switch (role) {
      case messenger:
        if (isSaving && knownTowersInfos.size() > 0) {
          // TODO: move to utils
          MapLocation destination = Utils.findClosestTower(knownTowersInfos, rc);

          Direction dir = rc.getLocation().directionTo(destination);
          // TODO: what happens if mopper is facing a wall?
          if (rc.canMove(dir)) {
            rc.move(dir);
          }
        }
        // NOTE: for debugging, remove when submitting
        rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);

        Utils.updateFriendlyTowers(rc);
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
      isSaving = true;
    }
  }

  public static void determineMopperRole(RobotController rc) {
    int id = rc.getID();
    switch (id % 2) {
      case 0:
        role = MOPPER_ROLES.messenger;
      default:
        role = MOPPER_ROLES.normal;
    }
  }

}
