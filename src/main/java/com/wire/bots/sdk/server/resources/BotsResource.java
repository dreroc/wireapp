//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.bots.sdk.server.resources;

import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.crypto.Crypto;
import com.wire.bots.sdk.factories.CryptoFactory;
import com.wire.bots.sdk.factories.StorageFactory;
import com.wire.bots.sdk.server.model.NewBot;
import com.wire.bots.sdk.server.model.NewBotResponseModel;
import com.wire.bots.sdk.tools.AuthValidator;
import com.wire.bots.sdk.tools.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/bots")
public class BotsResource {
    private final MessageHandlerBase handler;

    private final StorageFactory storageF;
    private final CryptoFactory cryptoF;
    private final AuthValidator validator;

    public BotsResource(MessageHandlerBase handler, StorageFactory storageF, CryptoFactory cryptoF, AuthValidator validator) {
        this.handler = handler;
        this.storageF = storageF;
        this.cryptoF = cryptoF;
        this.validator = validator;
    }

    @POST
    public Response newBot(@HeaderParam("Authorization") String auth, NewBot newBot) throws Exception {
        if (!validator.validate(auth)) {
            Logger.warning("Invalid auth '%s'", auth);
            return Response.
                    ok("Invalid Authorization: " + auth).
                    status(403).
                    build();
        }

        if (!handler.onNewBot(newBot))
            return Response.
                    status(409).
                    build();


        String botId = newBot.id;
        boolean saveState = storageF.create(botId).saveState(newBot);
        if (!saveState) {
            Logger.warning("Failed to save the state. Bot: %s", botId);
        }

        NewBotResponseModel ret = new NewBotResponseModel();
        ret.name = handler.getName();
        ret.accentId = handler.getAccentColour();
        String profilePreview = handler.getSmallProfilePicture();
        if (profilePreview != null) {
            ret.addAsset(profilePreview, "preview");
        }

        String profileBig = handler.getBigProfilePicture();
        if (profileBig != null) {
            ret.addAsset(profileBig, "complete");
        }

        Crypto crypto = cryptoF.create(botId);
        ret.lastPreKey = crypto.newLastPreKey();
        ret.preKeys = crypto.newPreKeys(0, newBot.conversation.members.size() * 8);

        return Response.
                ok(ret).
                status(201).
                build();
    }
}