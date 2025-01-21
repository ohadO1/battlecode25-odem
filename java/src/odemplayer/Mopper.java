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
import battlecode.common.*;

public class Mopper extends Globals {

  private enum MOPPER_ROLES {
    messenger,
    refiller,
    normal,
    attack
  }

  private enum MOPPER_STATE {
    transferPaint,
    notifyTower,
    roam,
    saving,
    attack,
    refillAlly,
    enemyDetected
  }

  static MOPPER_TASKS task = null;

  enum MOPPER_TASKS {
    transferPaint,
    attack,
    remove_enemy_paint
  }

  private static MOPPER_ROLES role = MOPPER_ROLES.normal;
  private static MOPPER_STATE state = MOPPER_STATE.roam;
  static MOPPER_STATE statePrev = state;
  private static MapLocation towerDestination = null;
  static boolean stateChanged = false;
  static RobotInfo allyToRefill = null;
  static int refillWait = 0;
  static RobotInfo refillTower;

  public static void runMopper(RobotController rc) throws GameActionException {
    Utils.updateFriendlyTowers(rc);
    // TODO: add a role that will support soldiers and refill them from tower
    // contantly
    // TODO: add check if task != null

    for (MapInfo tile : rc.senseNearbyMapInfos()) {
      RobotInfo potentialEnemy = rc.senseRobotAtLocation(tile.getMapLocation());
      if (potentialEnemy != null && (potentialEnemy.team != rc.getTeam())) {
        if (rc.canMopSwing(rc.getLocation().directionTo(potentialEnemy.getLocation()))) {
          rc.mopSwing(rc.getLocation().directionTo(potentialEnemy.getLocation()));
        }
      }
    }

    // transfer paint
    if (allyToRefill == null) {
      for (RobotInfo robot : rc.senseNearbyRobots()) {
        if (robot.getType() == UnitType.SOLDIER || robot.getType() == UnitType.SPLASHER && robot.team == rc.getTeam()) {
          if (((double) robot.getPaintAmount()) / robot.getType().paintCapacity <= 0.3 || rc.getPaint() <= 30) {
            state = MOPPER_STATE.refillAlly;
            allyToRefill = robot;
          }
        }
      }
    }

    switch (state) {
      case roam:
        rc.setIndicatorString("in roam");
        Utils.roamGracefullyf(rc);
        // find out more about attcks
        // mopperAttack(rc, nextLoc);
        if (checkNearbyRuins(rc) && knownTowersInfos.size() > 0) {
          state = MOPPER_STATE.notifyTower;
          towerDestination = Utils.findClosestTower(knownTowersInfos, rc);
          PathFinder.moveToLocation(rc, towerDestination);
          if (towerDestination != null) {
            state = MOPPER_STATE.notifyTower;
          }
        }
        break;
      // case messenger:
      case notifyTower:
        if (towerDestination == null) {
          state = MOPPER_STATE.roam;
          break;
        }
        PathFinder.moveToLocation(rc, towerDestination);
        if (rc.canSendMessage(towerDestination)) {
          state = MOPPER_STATE.roam;
        }

        break;

      case refillAlly:
        rc.setIndicatorString("in refillAlly");
        int numToTransfer = rc.getPaint() > 60 ? 50 : rc.getPaint();
        if (rc.canTransferPaint(allyToRefill.location, numToTransfer)) {
          rc.transferPaint(allyToRefill.getLocation(), numToTransfer);
          allyToRefill = null;
          state = MOPPER_STATE.roam;
        } else {
          PathFinder.moveToLocation(rc, allyToRefill.getLocation());
        }
        break;

      case enemyDetected:
        mopperAttack(rc, rc.getLocation());
        state = MOPPER_STATE.roam;
        break;
      case saving:
        MapLocation destination = Utils.findClosestTower(knownTowersInfos, rc);
        PathFinder.moveToLocation(rc, destination);
        boolean didUpdateTowerToSave = Utils.updateFriendlyTowers(rc);
        // // NOTE: for debugging, remove when submitting
        rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
        if (didUpdateTowerToSave) {
          state = MOPPER_STATE.notifyTower;
        }
        break;

      default:
        break;
    }
  }

  // TODO: change it
  public static void mopperAttack(RobotController rc, MapLocation nextLoc) throws GameActionException {
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
