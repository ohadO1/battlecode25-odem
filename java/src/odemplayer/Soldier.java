package odemplayer;

import battlecode.common.*;

import java.util.Stack;

class Soldier extends Globals {

  enum SOLDIER_STATES{
    scoutDirection, //scout a specific direction as assigned by the tower.
    roam,           //walk randomly
    notifyTower,    //found a ruin im not able to build. walk to notify tower
    buildTower,     //found a ruin im able to build. build it.
    notifySaveChips,//not enough chips to build a tower. find a money tower to ask to save.
    seekRefill,     //look for a tower with paint to refill paint from.
    attack,         //found a tower with enough friends to attack it.
    sendMessages,   //go to the nearest tower and send it my message queue.
  }

  //tasks are like a macro state. they are persistent within states.
  //a soldier doesn't necessarily have a task at all times.
  static SOLDIER_TASKS task = null;
  enum SOLDIER_TASKS{
    buildTower,
    attack,
  }

  public static SOLDIER_STATES state = SOLDIER_STATES.roam;
  static SOLDIER_STATES statePrev = state;
  static boolean stateChanged = false;
  static int stateChangedRunAt = 0;
  static GAME_PHASE gamePhase = GAME_PHASE.early;

  /// state specific vars

  //notifyTower
  static MapLocation notifyDest;         //tower to notify ive found a ruin
  static MapLocation notifiedToSaveFor;  //save the ruinDest I asked to save for, to not ask again.
  static Stack<Integer> messagesStack = new Stack<>();
  static MapLocation sendMessageDest;


  //build tower
  static UnitType towerToBuild;

  //ask to save
  static MapLocation askToSaveDest;

  static int refillWait;
  static RobotInfo refillTower;

  //task specific vars
  static MapInfo ruinDest = null; //ruin im aiming to build a tower at
  static RobotInfo towerTarget = null; //tower im aiming to ruin
  static boolean askedTowerToSendMopper = false;

  // TODO: if waiting too long for saving money, consider going to ask more towers to save.
  // TODO: stop building tower if pattern is done and someone else is on it

  public static void runSoldier(RobotController rc) throws GameActionException {

    Utils.updateFriendlyTowers(rc);
    String indicatorMessage = "";

    // ==== seek refill ====
    if(((double)rc.getPaint())/rc.getType().paintCapacity < SOLDIER_PAINT_FOR_URGENT_REFILL)
      state = SOLDIER_STATES.seekRefill;

    //game phase
    int rounds = rc.getRoundNum();
    gamePhase = GAME_PHASE.early;
    if(rounds > EARLY_GAME_END) gamePhase = GAME_PHASE.mid;
    if(rounds > MID_GAME_END) gamePhase = GAME_PHASE.late;

    switch (state) {
      //region roam
      case roam:

        if(stateChanged){

          if(task == SOLDIER_TASKS.buildTower){
            state = SOLDIER_STATES.buildTower;
            break;
          }
          if(task == SOLDIER_TASKS.attack){
            state = SOLDIER_STATES.attack;
            break;
          }

          notifyDest = null;
          refillTower = null;
          askToSaveDest = null;
        }

        //roam algorithms
        if(spawnPoint == null) spawnPoint = rc.getLocation();

        //look for ruins and enemy towers
        ruinDest = null;
        towerTarget = null;
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int currentDistance = 99999;
        int towerDistance = 99999;
        for (MapInfo tile : nearbyTiles) {

          //detect ruins
          if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
            int distance = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
            if (distance < currentDistance) {
              ruinDest = tile;
              currentDistance = distance;
              System.out.println("found ruin at: " + ruinDest.getMapLocation() + ", robot at location: " + rc.senseRobotAtLocation(tile.getMapLocation()));
            }
          }

          //detect tower
          RobotInfo robot = rc.senseRobotAtLocation(tile.getMapLocation());
          if(robot != null && robot.getType().isTowerType() && robot.getTeam() != rc.getTeam()
                  && rc.getLocation().distanceSquaredTo(robot.getLocation()) < towerDistance){
            towerDistance = rc.getLocation().distanceSquaredTo(robot.getLocation());
            towerTarget = robot;
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
        //if found enemy tower, decide what to do. (has priority over the above ruin)
        if(towerTarget != null){
          if(Utils.shouldIAttackTower(rc,towerTarget,gamePhase)){
            task = SOLDIER_TASKS.attack;
            state = SOLDIER_STATES.attack;
          }
        }
        //if didn't find ruin, wander around.
        else {
          MapLocation nextLoc = null;

          if(gamePhase == GAME_PHASE.early)
          {
            nextLoc = Utils.roamCircle(rc);
          }
          else if(!reachedCenter){
            nextLoc = Utils.roamDest(rc);
          }
          else nextLoc = Utils.roamGracefullyf(rc);

          if (rc.canAttack(nextLoc)) {
            MapInfo nextLocInfo = rc.senseMapInfo(nextLoc);
            if (!nextLocInfo.getPaint().isAlly()) {
              rc.attack(nextLoc);
            }
          }

        }

        // ==== seek refill ====
        if(((double)rc.getPaint())/rc.getType().paintCapacity < SOLDIER_PAINT_FOR_CASUAL_REFILL)
          state = SOLDIER_STATES.seekRefill;

        break;
      //endregion
      //region build tower
      case SOLDIER_STATES.buildTower:

        //go to the ruin
        MapLocation targetLocation = ruinDest.getMapLocation();
        Direction dir = rc.getLocation().directionTo(targetLocation);
        PathFinder.moveToLocation(rc,targetLocation);                   //may conflict later in this state, maybe stop moving once there.

        towerToBuild = Utils.WhatShouldIBuild(rc,targetLocation,gamePhase);
        indicatorMessage = "building " + towerToBuild + " at " + ruinDest.getMapLocation() + ", prio: " + idealTowerOrder.size();

        //someone else built it
        if(rc.canSenseLocation(targetLocation) && rc.senseRobotAtLocation(targetLocation) != null){
          task = null;
          state = SOLDIER_STATES.roam;
          break;
        }

        //try to mark
        MapLocation checkMarked = targetLocation.subtract(dir);
        if (rc.canSenseLocation(checkMarked) && rc.senseMapInfo(checkMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(towerToBuild, targetLocation)) {
          rc.markTowerPattern(towerToBuild, targetLocation);
        }

        //loop through marks. attack whatever is possible
        boolean attacked = false;
        MapInfo[] patternTiles = rc.senseNearbyMapInfos(targetLocation, 8);
        for (int i = 0; i < patternTiles.length && !attacked; i++) {

          //found enemy paint
          if(patternTiles[i].getPaint() == PaintType.ENEMY_PRIMARY || patternTiles[i].getPaint() == PaintType.ENEMY_SECONDARY)
          if(!askedTowerToSendMopper)
          {
            askedTowerToSendMopper = true;
            messagesStack.add(Utils.encodeMessage(MESSAGE_TYPE.sendMopperToClearRuin,ruinDest.getMapLocation()));
          }

          //found a paintable spot
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
        if (towerToBuild != null) {

          //not enough money. go ask tower to save
          if(rc.getChips() < towerToBuild.moneyCost && notifiedToSaveFor != ruinDest.getMapLocation()){
            state = SOLDIER_STATES.notifySaveChips;
          }
          //enough chips/already notified. build tower.
          if(rc.canCompleteTowerPattern(towerToBuild,targetLocation)){
            askedTowerToSendMopper = false;
            rc.completeTowerPattern(towerToBuild, targetLocation);
            state = SOLDIER_STATES.roam;
            task = null;
          }

        }

        break;
      //endregion
      //region notify tower about a ruin i need help with
        case SOLDIER_STATES.notifyTower:

          //find dest
          if(stateChanged)
            notifyDest = Utils.findClosestTower(knownTowersInfos, rc);

          //goto dest
          PathFinder.moveToLocation(rc, notifyDest);

          //reach dest, send message.
          if(rc.canSendMessage(notifyDest)) {
            rc.sendMessage(notifyDest,Utils.encodeMessage(MESSAGE_TYPE.buildTowerHere,ruinDest.getMapLocation()));

            if((double) rc.getPaint() /rc.getType().paintCapacity > SOLDIER_PAINT_FOR_TASK)
              state = SOLDIER_STATES.buildTower;
            else state = SOLDIER_STATES.seekRefill;
          }
        break;
        //endregion
      //region ask tower to save chips
      case SOLDIER_STATES.notifySaveChips:

        //someone else built it
        if(rc.canSenseLocation(ruinDest.getMapLocation()) && rc.senseRobotAtLocation(ruinDest.getMapLocation()) != null){
          task = null;
          state = SOLDIER_STATES.roam;
        }

        //find tower to ask to save chips
        if(stateChanged || askToSaveDest == null){

          //look for a money tower
          askToSaveDest = Utils.findClosestTower(knownTowersInfos, rc, new UnitType[]{UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_TWO_MONEY_TOWER, UnitType.LEVEL_THREE_MONEY_TOWER});

          //I didnt find any money tower, pick whatever.
          askToSaveDest = Utils.findClosestTower(knownTowersInfos, rc);

          //i was born at a tower so i must know atleast one. but as failsafe
          if(askToSaveDest == null) {
            indicatorMessage = "found no tower to ask to send messages.";
            state = SOLDIER_STATES.roam;
            break;
          }

          //make sure i have decided what tower i wanna build
          if(towerToBuild == null) towerToBuild = Utils.WhatShouldIBuild(rc,ruinDest.getMapLocation(),gamePhase);
        }

        //goto the tower I chose
        PathFinder.moveToLocation(rc,askToSaveDest);

        //send message
        if(rc.canSendMessage(askToSaveDest)){

          rc.sendMessage(askToSaveDest,Utils.encodeMessage(MESSAGE_TYPE.saveChips,towerToBuild.moneyCost));
          notifiedToSaveFor = ruinDest.getMapLocation();

          //ask for a refill
          if((double) rc.getPaint() /rc.getType().paintCapacity < SOLDIER_PAINT_FOR_TASK){
            state = SOLDIER_STATES.seekRefill;
          }
          else state = SOLDIER_STATES.buildTower;
        }

      break;
      //endregion
      //region notify tower
      case SOLDIER_STATES.sendMessages:

        //done
        if(messagesStack.isEmpty()){

          //get a refill while im at it
          if((double)rc.getPaint() / rc.getType().paintCapacity <= SOLDIER_PAINT_FOR_CASUAL_REFILL)
            state = SOLDIER_STATES.seekRefill;
          else
            state = SOLDIER_STATES.roam;
          break;
        }

        //find tower to ask to save chips
        if(stateChanged || sendMessageDest == null){

          //pick a tower
          sendMessageDest = Utils.findClosestTower(knownTowersInfos,rc);
        }

        //goto the tower I chose
        PathFinder.moveToLocation(rc,sendMessageDest);

        //send message
        if(rc.canSendMessage(sendMessageDest)){

          rc.sendMessage(sendMessageDest,messagesStack.pop());
        }

        break;
      //endregion
      //region seek a refill
      case SOLDIER_STATES.seekRefill:

        //abort
        if((double)rc.getPaint()/rc.getType().paintCapacity > SOLDIER_PAINT_FOR_CASUAL_REFILL)
          state = SOLDIER_STATES.roam;

        int missingPaint = rc.getType().paintCapacity - rc.getPaint();
        if(stateChanged || refillTower == null) {
          stateChangedRunAt = rc.getRoundNum();
  //          System.out.print("looking for tower ... ");
          refillTower = null;

          for(RobotInfo tower : knownTowersInfos){
            if(refillTower == null ||
                  (tower.getLocation().distanceSquaredTo(rc.getLocation()) < refillTower.getLocation().distanceSquaredTo(rc.getLocation()))
                  && tower.getPaintAmount() >= missingPaint)
              refillTower = tower;
          }

          //no tower has enough paint. just go to the nearest and wait there.
          if(refillTower == null) refillTower = Utils.findClosestTowerInfo(knownTowersInfos,rc);
        }

        if(refillTower == null) System.out.println("still didnt find any tower. state changed run at: " + stateChangedRunAt);

        //move
        PathFinder.moveToLocation(rc,refillTower.getLocation());

        //am i there yet?
        if(!rc.canTransferPaint(refillTower.getLocation(),0)){

          if(rc.canTransferPaint(refillTower.getLocation(),-missingPaint))
          {
            rc.transferPaint(refillTower.getLocation(),-missingPaint);
            state = SOLDIER_STATES.roam;
          }
        }

      break;
      //endregion
      //region attack towers
      case SOLDIER_STATES.attack:

        MapLocation target = towerTarget.getLocation();

        //approach tower if cant attack
        if(rc.getLocation().distanceSquaredTo(target) >= 3)
          PathFinder.moveToLocation(rc,target);

        //attack
        if(rc.canAttack(target))
          rc.attack(target);

        //should i give up at some point?

        //done
        if(towerTarget.getHealth() <= 0)
        {
          towerTarget = null;
          task = null;
          state = SOLDIER_STATES.roam;
        }

      break;
      //endregion
    }

    //if i have messages in queue, switch to notify state
    if(!messagesStack.isEmpty()) {
      state = SOLDIER_STATES.sendMessages;
      indicatorMessage += "\nmessages: {" + messagesStack.size() + "}: " + (new DecodedMessage<>(messagesStack.peek()));
    }

    //states
    stateChanged = state != statePrev;
    statePrev = state;

    //attack current location
    MapLocation loc = rc.getLocation();
    if (rc.canAttack(loc) && task != SOLDIER_TASKS.attack) {
      MapInfo nextLocInfo = rc.senseMapInfo(loc);
      if (!nextLocInfo.getPaint().isAlly()) {
        rc.attack(loc);
      }
    }

    rc.setIndicatorString("state: " + state.name() + ", changed: " + (stateChanged ? 1 : 0) + "\nknown towers: " + knownTowersInfos.size() + "\n" + indicatorMessage);
  }

}
