package net.onebeastchris.geyser.extension.pickpack.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.onebeastchris.geyser.extension.pickpack.Form;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.geysermc.geyser.session.GeyserSession;

import java.util.*;

import static net.onebeastchris.geyser.extension.pickpack.PickPack.config;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.loader;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.logger;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.storage;

public class PacketInterceptor extends ChannelDuplexHandler {
    private static final String HANDLER_NAME = "pickpack-interceptor";
    private final GeyserSession session;
    private final String xuid;
    private boolean handledStack = false;

    public PacketInterceptor(GeyserSession session, String xuid) {
        this.session = session;
        this.xuid = xuid;
    }

    public static void attach(GeyserSession session, String xuid) {
        try {
            Channel channel = session.getUpstream().getSession().getPeer().getChannel();
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
            if (pipeline.get("bedrock-packet-codec") != null) {
                pipeline.addAfter("bedrock-packet-codec", HANDLER_NAME, new PacketInterceptor(session, xuid));
            } else {
                pipeline.addLast(HANDLER_NAME, new PacketInterceptor(session, xuid));
            }
            logger.debug("Attached packet interceptor for XUID: " + xuid);
        } catch (Exception e) {
            logger.error("Failed to attach packet interceptor for " + xuid + ": " + e.getMessage());
            if (logger.isDebug()) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BedrockPacketWrapper wrapper) {
            BedrockPacket packet = wrapper.getPacket();
            if (packet instanceof CommandRequestPacket cmdPacket) {
                String cmd = cmdPacket.getCommand().trim();
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1).trim();
                }
                if (cmd.equalsIgnoreCase("pickpack")) {
                    session.executeInEventLoop(() -> {
                        if (session.hasPermission(config.menuPermission())) {
                            Form form = new Form(session);
                            form.send();
                        } else {
                            session.sendMessage(LanguageManager.getLocaleString(session.locale(), "geyser.command.permission_fail"));
                        }
                    });
                    return;
                }
            }
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!handledStack && msg instanceof BedrockPacketWrapper wrapper) {
            BedrockPacket packet = wrapper.getPacket();
            if (packet instanceof ResourcePackStackPacket stackPacket) {
                handledStack = true;
                handleResourcePackStack(stackPacket);
            }
        }
        super.write(ctx, msg, promise);
    }

    private void handleResourcePackStack(ResourcePackStackPacket stackPacket) {
        try {
            Map<String, ResourcePackStackPacket.Entry> existingMap = new LinkedHashMap<>();
            for (ResourcePackStackPacket.Entry entry : stackPacket.getResourcePacks()) {
                existingMap.put(entry.getPackId(), entry);
            }

            List<PackEntry> playerEntries = storage.getPackEntries(xuid);
            List<ResourcePackStackPacket.Entry> ordered = new ArrayList<>();

            for (PackEntry playerEntry : playerEntries) {
                ResourcePackStackPacket.Entry old = existingMap.remove(playerEntry.uuid());
                String version = old != null ? old.getPackVersion() : "";
                if (version.isEmpty() && loader.PACKS_INFO.containsKey(playerEntry.uuid())) {
                    version = loader.PACKS_INFO.get(playerEntry.uuid()).header().version().toString();
                }
                if (version.isEmpty() && old == null) {
                    continue;
                }
                String subpack = playerEntry.subpack() != null ? playerEntry.subpack() : "";
                ordered.add(new ResourcePackStackPacket.Entry(playerEntry.uuid(), version, subpack));
            }

            // Keep any other packs that were in the stack (e.g. Geyser internal packs)
            ordered.addAll(existingMap.values());

            stackPacket.getResourcePacks().clear();
            stackPacket.getResourcePacks().addAll(ordered);
            logger.debug("Reordered and configured " + ordered.size() + " packs in ResourcePackStackPacket for " + xuid);
        } catch (Exception e) {
            logger.error("Failed to update ResourcePackStackPacket for " + xuid + ": " + e.getMessage());
            if (logger.isDebug()) {
                e.printStackTrace();
            }
        }
    }
}
