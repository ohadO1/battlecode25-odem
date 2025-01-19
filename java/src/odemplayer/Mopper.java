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
    waitForRefill,
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
  static MapLocation allyToRefill = null;
  static int refillWait = 0;

  public static void runMopper(RobotController rc) throws GameActionException {
    Utils.updateFriendlyTowers(rc);
    // TODO: add a role that will support soldiers and refill them from tower
    // contantly
    //TODO: add check if task != null
    

    switch (state) {
      case roam:
        MapLocation nextLoc = Utils.roamGracefullyf(rc);
        mopperAttack(rc, nextLoc);

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
          rc.sendMessage(towerDestination, Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere, rc.getLocation()));
          if (rc.getPaint() < rc.getType().paintCapacity) {
            rc.sendMessage(towerDestination, Utils.encodeMessage(MESSAGE_TYPE.askForRefill, rc.getLocation()));
            state = MOPPER_STATE.waitForRefill;
          } else {
            state = MOPPER_STATE.roam;
          }
        }        

        break;
      case waitForRefill:
      //TODO: update to the new version of refill Itay is working on
        if(stateChanged) refillWait = 0;
        refillWait++;
        if(refillWait%10 == 0) rc.setIndicatorString("waiting for refill for " + refillWait + " turns.");
        break;
      
      case refillAlly:
        if (allyToRefill == null) {
          state = MOPPER_STATE.roam;
          break;
        }
        PathFinder.moveToLocation(rc, allyToRefill);
        if (rc.canTransferPaint(allyToRefill,  rc.getType().paintCapacity - rc.getPaint())) { //to fix because it can communicate with other robots
          rc.transferPaint(allyToRefill,  rc.getType().paintCapacity - rc.getPaint()); // to fix too
          state = MOPPER_STATE.roam;
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

      default:
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
