import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.Simple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class App extends GameServerRoomEventHandler {

    private Logger log = LoggerFactory.getLogger("App");
    private GreedyStarRoom room = null;
    private Map<Long, GreedyStarRoom> roomMap = new ConcurrentHashMap(256);
//    private Map<Long,ArrayList<Food>> fondMap = new HashMap<>();



    public static void main(String[] args) {
        String[] path = new String[1];


        /**
         * 本地调试时在此处填写自己config.Json的绝对路径,正式发布上线注释下面代码即可。
         */
        path[0] = "E:\\project\\GreedyStar\\GameServer\\Config.json";
        try {
            Main.main(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onGameClientEvent(Simple.Package.Frame clientEvent, StreamObserver<Simple.Package.Frame> clientChannel) throws InvalidProtocolBufferException {
        super.onGameClientEvent(clientEvent, clientChannel);
        Gsmvs.Request request = Gsmvs.Request.parseFrom(clientEvent.getMessage());
        switch (clientEvent.getCmdId()) {
            case Gshotel.HotelGsCmdID.HotelBroadcastCMDID_VALUE:
                Gshotel.HotelBroadcast boardMsg = Gshotel.HotelBroadcast.parseFrom(clientEvent.getMessage());
                String msg = boardMsg.getCpProto().toStringUtf8();
                try {
                    examplePush(boardMsg.getRoomID(),boardMsg.getUserID(),msg);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            case Gsmvs.MvsGsCmdID.MvsCreateRoomReq_VALUE:
                log.info("请求创建房间: 房间ID："+ request.getRoomID());
                break;
            //删除房间
            case Gshotel.HotelGsCmdID.HotelCloseConnet_VALUE:
                log.info("接到 Hotel Close Connet 消息: status："+ Gshotel.CloseConnectAck.parseFrom(clientEvent.getMessage()).getStatus());
                break;
            // 玩家checkin
            case Gshotel.HotelGsCmdID.HotelPlayerCheckin_VALUE:
                log.info("玩家进入房间成功(CheckIn):  userID:"+request.getUserID());
                JoinRoom(request,clientChannel);
                log.info("加入房间的通道【hotel】 channel "+Utils.getChannel(clientChannel));
                break;
            case Gsmvs.MvsGsCmdID.MvsJoinRoomReq_VALUE:
                log.info("请求进入房间(JoinRoom)  玩家"+request.getUserID()+"进入房间，房间ID为："+request.getRoomID());
                log.info("请求进入房间  【 mvs 】 channel "+Utils.getChannel(clientChannel));
                break;
            case Gsmvs.MvsGsCmdID.MvsKickPlayerReq_VALUE:
                log.info("请求踢人: 房间："+request.getRoomID()+"玩家："+request.getUserID()+"被踢出");
                break;
            case Gsmvs.MvsGsCmdID.MvsLeaveRoomReq_VALUE:
                leaveRoom(request);
                log.info("请求离开房间成功： 玩家"+request.getUserID()+"离开房间，房间ID为："+request.getRoomID());
                break;
            case Gsmvs.MvsGsCmdID.MvsJoinOpenReq_VALUE:
                log.info("请求房间打开:  roomID："+ request.getRoomID());
                break;
            case Gsmvs.MvsGsCmdID.MvsJoinOverReq_VALUE:
                log.info("请求房间关闭: roomID："+ request.getRoomID());
                break;
            case Gsmvs.MvsGsCmdID.MvsSetRoomPropertyReq_VALUE:
                Gsmvs.SetRoomPropertyReq roomPropertyReq = Gsmvs.SetRoomPropertyReq.parseFrom(clientEvent.getMessage());
                log.info("修改房间属性: ");
                log.info(roomPropertyReq+"");
                break;
            case Gsmvs.MvsGsCmdID.MvsGetRoomDetailPush_VALUE:
                Gsmvs.RoomDetail roomDetail = Gsmvs.RoomDetail.parseFrom(clientEvent.getMessage());
                log.info("主动获取房间回调:");
                log.info(roomDetail+"");
                break;
            case Gshotel.HotelGsCmdID.GSSetFrameSyncRateNotifyCMDID_VALUE:
                Gshotel.GSSetFrameSyncRateNotify setFrameSyncRateNotify = Gshotel.GSSetFrameSyncRateNotify.parseFrom(clientEvent.getMessage());
                log.info("帧率通知");
                log.info(setFrameSyncRateNotify+"");
                break;
            case Gshotel.HotelGsCmdID.GSFrameDataNotifyCMDID_VALUE:
                Gshotel.GSFrameDataNotify frameDataNotify = Gshotel.GSFrameDataNotify.parseFrom(clientEvent.getMessage());
                log.info("帧数据通知");
                log.info(frameDataNotify+"");
                break;
            case Gshotel.HotelGsCmdID.GSFrameSyncNotifyCMDID_VALUE:
                Gshotel.GSFrameSyncNotify frameSyncNotify = Gshotel.GSFrameSyncNotify.parseFrom(clientEvent.getMessage());
                log.info("帧同步通知");
                log.info(frameSyncNotify+"");
                break;
            case Gsmvs.MvsGsCmdID.MvsNetworkStateReq_VALUE:
                log.info("NetworkStateReq channel "+Utils.getChannel(clientChannel));
                break;

        }
        return false;
    }




    /**
     * 玩家加入房间
     * @param request 返回的数据
     * @param clientChannel 房间信息通道
     */
    private void JoinRoom(Gsmvs.Request request, StreamObserver<Simple.Package.Frame> clientChannel) {
        GameServerMsg msg;
        long roomID = request.getRoomID();
//        room.fondList = new ArrayList<>();
        if (!roomMap.containsKey(roomID)) {
            roomMap.put(roomID, new GreedyStarRoom(roomID, clientChannel,this));
            room = roomMap.get(roomID);
            for (int i = room.foodList.size(); i < Const.FOOD_INITIAL_NUB; i++) {
                Food food = Food.addFood(room.foodNum);
                room.foodList.add(food);
                room.foodNum = room.foodList.size();
            }
        }
        roomAddUser1(room,request.getUserID());
        sendFoodMsg(room.foodList,roomID,request.getUserID());
        //给后进入的玩家同步前面的玩家的信息
        ArrayList<GreedStarUser> arrayList = new ArrayList<>();
        for (int i = 0; i < room.userList.size(); i++) {
            if (request.getUserID() != room.userList.get(i).userID) {
                arrayList.add(room.userList.get(i));
            }
        }
        if (arrayList.size() > 0 ) {
            msg = new GameServerMsg("otherPlayer",arrayList);
            log.info("otherPlayer"+JsonUtil.toString(msg));
            sendMsgToOtherUserInRoom(roomID, JsonUtil.toString(msg).getBytes(),new int[]{request.getUserID()});
        }

    }

    /**
     * 玩家离开房间
     * @param request
     */
    private void leaveRoom(Gsmvs.Request request) {
        long roomID = request.getRoomID();
        if (roomMap.containsKey(roomID)) {
            if (!roomRemoveUser1(roomMap.get(roomID),request.getUserID())) {
                logger.warn("not found userID:" + request.getUserID());
            } else{
                if (roomMap.get(request.getRoomID()).userList.size() <= 0 ) {
                    roomMap.get(request.getRoomID()).destroy();
                    roomMap.remove(request.getRoomID());
                }
            }
        } else {
            logger.warn("not found roomID:" + request.getRoomID());
        }
    }

    /**
     * 发送初始创建食物的信息
     * @param foodArrayList 食物列表
     * @param roomID 房间ID
     * @param userID 用户ID 用户ID为0，就给房间中全部用户发送消息
     */
    private void sendFoodMsg(ArrayList<Food> foodArrayList,long roomID,int userID) {
        List<Food> foods_one = foodArrayList.subList(0,19);
        List<Food> foods_two = foodArrayList.subList(20,39);
        List<Food> foods_three = foodArrayList.subList(40,59);
        List[] foods= new List[]{foods_one, foods_two, foods_three};
        for (List food : foods) {
            GameServerMsg msg = new GameServerMsg("addFood", food);
            if (userID == 0) {
                sendMsgToOtherUserInRoom(roomID, JsonUtil.toString(msg).getBytes());
            } else {
                sendMsgToOtherUserInRoom(roomID, JsonUtil.toString(msg).getBytes(), new int[]{userID});
            }
        }
    }




//    /**
//     * 添加星星
//     * @param foodArrayList 存放星星的ArrayList
//     * @return 放回
//     */
//    private ArrayList<Food> addFood(ArrayList<Food> foodArrayList) {
//        Food food = Food.addFood(room.foodNum);
//        log.info("addFood ID :"+ food.ID);
//        foodArrayList.add(food);
//        return foodArrayList;
//    }


    /**
     * 房间加入玩家
     * @param room 玩家
     * @param userID 用户ID
     */
    private void roomAddUser1(GreedyStarRoom room, int userID) {
        int[] Position =  Utils.getRandomPosition();
        Input input = new Input();
        GreedStarUser user = new GreedStarUser(userID,Const.USER_PREPARE,0,Const.USER_SIZE,Position[0],Position[1],Const.SPEED,input);
        if (room.userList != null) {
            for (int i = 0; i <room.userList.size() ; i++) {
                if (room.userList.get(i).userID == userID) {
                    log.warn("Players already exist :"+ userID );
                    return;
                }
            }
        } else {
            room.userList = new ArrayList<>();
        }
        room.userList.add(user);
        GameServerMsg msg = new GameServerMsg("addPlayer",user);
        log.info("addPlayer:"+JsonUtil.toString(msg));
        sendMsgToOtherUserInRoom(room.ID,(JsonUtil.toString(msg)).getBytes());
    }

    /**
     *
     * @param room 房间信息
     * @param userID 用户ID
     */
    private boolean roomRemoveUser1(GreedyStarRoom room, int userID) {
        for (int i = 0; i < room.userList.size(); i++) {
            if (userID == room.userList.get(i).userID) {
                GameServerMsg msg = new GameServerMsg("removePlayer",room.userList.get(i));
                room.userList.remove(i);
                sendMsgToAllUserInRoom(room.ID,JsonUtil.toString(msg).getBytes());
                return true;
            }
        }
        return false;
    }


    @Override
    public void onStart() {
        log.info("onStart");

    }

    @Override
    public void onStop() {
        log.info("onStop");
    }

    @Override
    public boolean onRoomEvent(Room room, Simple.Package.Frame receivedFrame, StreamObserver<Simple.Package.Frame> clientChannel) {
        return false;
    }



    /**
     * API使用示例
     * @param msg
     */
    private void examplePush(long roomID, int userID, String msg) throws JSONException {
        JSONObject jsonObject = new JSONObject(msg);
        switch ( jsonObject.getString("type")) {
            case "input":
                room = roomMap.get(roomID);
                //gs 停止时，玩家创建房间，gs 未存储房间，此处需要判断空,调试时常遇到
                if (room != null && room.userList != null) {
                    for (int i = 0; i <room.userList.size() ; i++) {
                        if (userID == room.userList.get(i).userID) {
                            room.userList.get(i).input = JsonUtil.fromJson(jsonObject.getString("data"),Input.class);
                            break;
                        }
                    }
                }
            break;
            //主动创建房间
            case "startGame":
                room = roomMap.get(roomID);
                if (room != null && room.userList != null) {
                    GameServerMsg gameServerMsg = new GameServerMsg("startGame",room.userList);
                    logger.info("发送主动创建房间开始游戏的消息");
                    sendMsgToOtherUserInRoom(roomID, JsonUtil.toString(gameServerMsg).getBytes());
                    sendFoodMsg(room.foodList,roomID,0);
                }
            break;
        }
    }
}