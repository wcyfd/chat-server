package com.randioo.chat_server.module.chat.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import org.apache.mina.core.session.IoSession;
import org.springframework.stereotype.Service;

import com.google.protobuf.GeneratedMessage;
import com.randioo.chat_server.cache.local.RoomCache;
import com.randioo.chat_server.entity.bo.Role;
import com.randioo.chat_server.entity.po.Room;
import com.randioo.chat_server.module.chat.ChatConstant;
import com.randioo.chat_server.protocol.Chat.ChatJoinRoomResponse;
import com.randioo.chat_server.protocol.Chat.ChatQuitRoomResponse;
import com.randioo.chat_server.protocol.Chat.ChatSendResponse;
import com.randioo.chat_server.protocol.Chat.SCChat;
import com.randioo.chat_server.protocol.Entity.ChatData;
import com.randioo.chat_server.protocol.Error.ErrorCode;
import com.randioo.chat_server.protocol.ServerMessage.SC;
import com.randioo.randioo_server_base.lock.CacheLockUtil;
import com.randioo.randioo_server_base.service.ObserveBaseService;
import com.randioo.randioo_server_base.utils.SessionUtils;

@Service("chatService")
public class ChatServiceImpl extends ObserveBaseService implements ChatService {

	@Override
	public GeneratedMessage addRoom(Role role, String roomId) {
		roomId = getRoomId(roomId);

		Room room = getRoom(roomId);
		room.getAccountSet().add(role.getAccount());
		role.getRoomIdSet().add(roomId);

		return SC.newBuilder().setChatJoinRoomResponse(ChatJoinRoomResponse.newBuilder()).build();
	}

	@Override
	public void quitAllRooms(Role role) {
		Set<String> roomIdSet = role.getRoomIdSet();
		for (String roomId : roomIdSet)
			quitRoom(role, roomId);
	}

	@Override
	public GeneratedMessage quitRoom(Role role, String roomId) {
		roomId = getRoomId(roomId);

		Map<String, Room> roomMap = RoomCache.getRoomMap();
		Room room = roomMap.get(roomId);
		String account = role.getAccount();
		if (room == null)
			return SC.newBuilder().setChatQuitRoomResponse(ChatQuitRoomResponse.newBuilder()).build();

		Lock lock = CacheLockUtil.getLock(Room.class, roomId);
		try {
			lock.lock();
			room = roomMap.get(roomId);
			if (room == null)
				return SC.newBuilder().setChatQuitRoomResponse(ChatQuitRoomResponse.newBuilder()).build();

			room.getAccountSet().remove(account);
			role.getRoomIdSet().remove(roomId);

			// 如果房间没有人了,则删除
			if (room.getAccountSet().size() == 0)
				roomMap.remove(roomId);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return SC.newBuilder().setChatQuitRoomResponse(ChatQuitRoomResponse.newBuilder()).build();
	}

	@Override
	public void send(IoSession session, Role role, String roomId, ChatData chatData) {
		roomId = getRoomId(roomId);

		if (!role.getRoomIdSet().contains(roomId)) {
			session.write(SC
					.newBuilder()
					.setChatSendResponse(
							ChatSendResponse.newBuilder().setErrorCode(ErrorCode.CONNECT_ERROR.getNumber())).build());
			return;
		}
		session.write(SC.newBuilder().setChatSendResponse(ChatSendResponse.newBuilder()).build());

		Room room = getRoom(roomId);
		Set<String> accountSet = room.getAccountSet();

		SC sc = SC.newBuilder().setSCChat(SCChat.newBuilder().setChatData(chatData)).build();
		for (String account : accountSet) {
			SessionUtils.sc(account, sc);
		}
	}

	/**
	 * 获得房间
	 * 
	 * @param roomId
	 * @return
	 * @author wcy 2017年6月7日
	 */
	private Room getRoom(String roomId) {
		Map<String, Room> roomMap = RoomCache.getRoomMap();
		Room room = roomMap.get(roomId);
		if (room != null)
			return room;
		Lock lock = CacheLockUtil.getLock(Room.class, roomId);
		try {
			lock.lock();
			room = roomMap.get(roomId);
			if (room != null)
				return room;

			room = new Room();
			roomMap.put(roomId, room);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}

		return room;

	}

	private String getRoomId(String roomId) {
		return ChatConstant.ROOM + roomId;
	}
}
