package odemplayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends Globals {

  public static void runMopper(RobotController rc) throws GameActionException {
    if (isMessanger == true) {
      System.out.println("IN IF STATEMENT, MARKING");
      rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
    }

    if (isMessanger && isSaving && knownTowers.size() > 0) {
      // TODO: move to utils
      MapLocation destination = Utils.findClosestTower(knownTowers, rc);

      Direction dir = rc.getLocation().directionTo(destination);
      // TODO: what happenes if mopper is facing a wall?
      if (rc.canMove(dir)) {
        rc.move(dir);
      }
    }

    MapLocation nextLoc = Utils.roamGracefullyf(rc);

    for (Direction tryMopDirection : directions) {
      if (rc.canMopSwing(tryMopDirection)) {
        rc.mopSwing(tryMopDirection);
      }
    }

    // TODO: attack by radius
    if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }

    // if we are a messanger, we also want to update friendly towers and check for
    // ruins
    if (isMessanger) {
      updateFriendlyTowers(rc);
      checkNearbyRuins(rc);
    }
  }

  public static void updateFriendlyTowers(RobotController rc) throws GameActionException {
    // Search for all nearby robots
    RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
    for (RobotInfo ally : allyRobots) {
      if (!ally.getType().isTowerType())
        continue;

      MapLocation allyLocation = ally.location;
      if (knownTowers.contains(allyLocation)) {
        if (isSaving) {
          if (rc.canSendMessage(allyLocation)) {
            rc.sendMessage(allyLocation, MessageType.SAVE_CHIPS.ordinal());
          }
          isSaving = false;
        }

        continue;
      }

      knownTowers.add(allyLocation);
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

}
