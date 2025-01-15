package odemplayer;

import antlr.collections.List;
import battlecode.common.*;

import java.nio.file.Path;
import java.util.ArrayList;

class Soldier extends Globals {

  enum SOLDIER_STATES{
    scoutDirection, //scout a specific direction as assigned by the tower.
    roam,           //walk randomly
    notifyTower,    //found a ruin im not able to build. walk to notify tower
    buildTower,     //found a ruin im able to build. build it.
    notifySaveChips,//not enough chips to build a tower. find a money tower to ask to save.
    waitForRefill,  //wait for a tower to send a mopper for a refill.
    attack,         //found a tower with enough friends to attack it.
  }

  //tasks are like a macro state. they are persistent within states.
  //a soldier doesn't necessarily have a task at all times.
  static SOLDIER_TASKS task = null;
  enum SOLDIER_TASKS{
    buildTower,
    attack,
  }

  static SOLDIER_STATES state = SOLDIER_STATES.roam;
  static SOLDIER_STATES statePrev = state;
  static boolean stateChanged = false;

  /// state specific vars


  //notifyTower
  static MapLocation notifyDest;    //tower to notify ive found a ruin

  //build tower
  static UnitType towerToBuild;

  //ask to save
  static MapLocation askToSaveDest;

  //ask for refill
  static int refillWait;

  //task specific vars
  static MapInfo ruinDest = null; //ruin im aiming to build a tower at

  // TODO: make moppers receive message of asking for a refill
  // TODO: attack state
  // TODO: scout direction state
  // TODO: search for enemy towers and call an attack
  // TODO: if notified tower about a ruin, consider also asking it to save money for building that tower.

  public static void runSoldier(RobotController rc) throws GameActionException {

    Utils.updateFriendlyTowers(rc);

    switch (state) {
      //region roam
      case roam:

        //search for the closest ruin in range
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int currentDistance = 99999;
        for (MapInfo tile : nearbyTiles) {
          if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
            int distance = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
            if (distance < currentDistance) {
              ruinDest = tile;
              currentDistance = distance;
              System.out.println("found ruin at: " + ruinDest.getMapLocation() + ", robot at location: " + rc.senseRobotAtLocation(tile.getMapLocation()));
            }
          }
        }

        //if found ruin, decide what to do about it.
        if (ruinDest != null) {
          if (Utils.ShouldIBuild(rc, knownTowersInfos))
            state = SOLDIER_STATES.buildTower;
          else
            state = SOLDIER_STATES.notifyTower;

          task = SOLDIER_TASKS.buildTower;
        }
        //if didn't find ruin, wander around.
        else {
          MapLocation nextLoc = Utils.roamGracefullyf(rc);

          if (rc.canAttack(nextLoc)) {
            MapInfo nextLocInfo = rc.senseMapInfo(nextLoc);
            if (!nextLocInfo.getPaint().isAlly()) {
              rc.attack(nextLoc);
            }
          }

        }
        break;
      //endregion
      //region build tower
      case SOLDIER_STATES.buildTower:

        if(stateChanged) System.out.println("starting to build tower at: " + ruinDest.getMapLocation());

        //go to the ruin
        MapLocation targetLocation = ruinDest.getMapLocation();
        Direction dir = rc.getLocation().directionTo(targetLocation);
        PathFinder.moveToLocation(rc,targetLocation);                   //may conflict later in this state, maybe stop moving once there.

        //try to mark
        MapLocation checkMarked = targetLocation.subtract(dir);
        if (rc.canSenseLocation(checkMarked) && rc.senseMapInfo(checkMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation)) {
          rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation);
        }

        //loop through marks. attack whatever is possible
        boolean attacked = false;
        MapInfo[] patternTiles = rc.senseNearbyMapInfos(targetLocation, 8);
        for (int i = 0; i < patternTiles.length && !attacked; i++) {
          if (patternTiles[i].getMark() != patternTiles[i].getPaint() && patternTiles[i].getMark() != PaintType.EMPTY) {

            //found a spot to paint. attack it
            if(rc.canAttack(patternTiles[i].getMapLocation())) {
              attacked = true;
              rc.attack(patternTiles[i].getMapLocation(), patternTiles[i].getMark() == PaintType.ALLY_SECONDARY);
            }
            //else, go there in order to reach.
            else {
              PathFinder.moveToLocation(rc,patternTiles[i].getMapLocation());
            }
          }
        }

        //complete tower building
        towerToBuild = Utils.WhatShouldIBuild(rc,targetLocation);
        System.out.println("chose type: " + towerToBuild);
        if (towerToBuild != null) {

          //not enough money. go ask tower to save
          if(rc.getChips() < towerToBuild.moneyCost){
            state = SOLDIER_STATES.notifySaveChips;
          }
          rc.completeTowerPattern(towerToBuild, targetLocation);
          state = SOLDIER_STATES.roam;
        }

        break;
      //endregion
      //region notify tower
        case SOLDIER_STATES.notifyTower:

          //find dest
          if(stateChanged)
            notifyDest = Utils.findClosestTower(knownTowersInfos, rc);

          //goto dest
          PathFinder.moveToLocation(rc, notifyDest);

          //reach dest, send message.
          if(rc.canSendMessage(notifyDest)) {
            rc.sendMessage(notifyDest,Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere,ruinDest.getMapLocation()));

            if(rc.getPaint()/rc.getType().paintCapacity > SOLDIER_PAINT_FOR_TASK)
              state = SOLDIER_STATES.buildTower;
            else{
              Clock.yield();
              rc.sendMessage(notifyDest,Utils.encodeMessage(MESSAGE_TYPE.askForRefill,notifyDest));
              state = SOLDIER_STATES.waitForRefill;
            }

          }
        break;
        //endregion
      //region notify to save chips
      case SOLDIER_STATES.notifySaveChips:

        //find tower to ask to save chips
        if(stateChanged){

          //look for a money tower
          askToSaveDest = Utils.findClosestTower(knownTowersInfos, rc, new UnitType[]{UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_THREE_MONEY_TOWER, UnitType.LEVEL_TWO_MONEY_TOWER});

          //i dont know any money tower. ask any tower, he'll take care of it
          askToSaveDest = Utils.findClosestTower(knownTowersInfos,rc);

          //i was born at a tower so i must know atleast one. but as failsafe
          if(askToSaveDest == null) state = SOLDIER_STATES.buildTower;

          //make sure i have decided what tower i wanna build
          if(towerToBuild == null) towerToBuild = Utils.WhatShouldIBuild(rc,ruinDest.getMapLocation());
        }

        //goto the tower I chose
        PathFinder.moveToLocation(rc,askToSaveDest);

        //send message
        if(rc.canSendMessage(askToSaveDest)){

          rc.sendMessage(askToSaveDest,Utils.encodeMessage(MESSAGE_TYPE.saveChips,towerToBuild.moneyCost));

          //ask for a refill
          if(rc.getPaint()/rc.getType().paintCapacity < SOLDIER_PAINT_FOR_TASK){
            Clock.yield();
            rc.sendMessage(askToSaveDest,Utils.encodeMessage(MESSAGE_TYPE.askForRefill,rc.getLocation()));
            state = SOLDIER_STATES.waitForRefill;
          }
          else state = SOLDIER_STATES.buildTower;
        }

      break;
    //endregioni
      //region wait for a refill
      case SOLDIER_STATES.waitForRefill:
      if(stateChanged) refillWait = 0;
      refillWait++;
      if(refillWait%10 == 0) System.out.println("waiting for refill for " + refillWait + " turns.");
      break;
      //endregion
    }

    //states
    stateChanged = state != statePrev;
    statePrev = state;
    if(stateChanged) System.out.println("state changed: " + state.name());

  }

}
