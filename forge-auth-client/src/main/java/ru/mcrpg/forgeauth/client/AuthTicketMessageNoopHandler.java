package ru.mcrpg.forgeauth.client;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class AuthTicketMessageNoopHandler implements IMessageHandler<AuthTicketMessage, IMessage> {

    @Override
    public IMessage onMessage(AuthTicketMessage message, MessageContext ctx) {
        return null;
    }
}
