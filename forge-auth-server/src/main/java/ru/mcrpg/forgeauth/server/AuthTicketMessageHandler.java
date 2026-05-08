package ru.mcrpg.forgeauth.server;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class AuthTicketMessageHandler implements IMessageHandler<AuthTicketMessage, IMessage> {

    @Override
    public IMessage onMessage(AuthTicketMessage message, MessageContext ctx) {
        ForgeAuthServerMod.getLifecycle().onTicketMessage(message, ctx);
        return null;
    }
}
