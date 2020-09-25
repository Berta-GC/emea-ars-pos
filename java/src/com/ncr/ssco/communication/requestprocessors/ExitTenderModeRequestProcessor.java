package com.ncr.ssco.communication.requestprocessors;

import com.ncr.Itemdata;
import com.ncr.ssco.communication.entities.pos.SscoError;
import com.ncr.ssco.communication.manager.SscoMessageHandler;
import com.ncr.ssco.communication.requestdecoder.RequestFromSsco;
import com.ncr.ssco.communication.responseencoder.ResponseToSsco;

/**
 * Created by NCRDeveloper on 06/06/2017.
 */
public class ExitTenderModeRequestProcessor extends TransactionProcessor {

    public ExitTenderModeRequestProcessor(SscoMessageHandler messageHandler) {
        super(messageHandler);
    }

    @Override
    public void process(RequestFromSsco requestFromSsco) {
        logger.debug("Enter");

        getManager().setTenderRounding(0);
        getManager().updateRoundDiscount(0);
        getManager().updateTotalAmount_fromSave();
        sendTotalsResponse(new SscoError());

        if (!getManager().exitTenderModeRequest()) {
            logger.warn("-- Warning ");
        }

        logger.debug("Exit");
    }

    @Override
    public void sendResponses(SscoError sscoError) {
        logger.debug("Enter");

        int itmno = 10000;
        for (Itemdata itmd : getManager().getTransactionalRewards()) {
            ResponseToSsco responseToSsco = getMessageHandler().createResponseToSsco("ItemVoided");
            responseToSsco.setIntField("ItemNumber", itmno++);
            /*
            responseToSsco.setStringField("Description", "sconto transazionale");
            responseToSsco.setIntField("DiscountAmount", (int) 0);
            responseToSsco.setStringField("DiscountDescription.1", itmd.text);
            responseToSsco.setIntField("AssociateItemNumber", 0);
            responseToSsco.setIntField("RewardLocation", 1);
            */
            getMessageHandler().sendResponseToSsco(responseToSsco);
        }
        getManager().updateTransactionalDiscount(0);

        if (sscoError.getCode() != SscoError.OK) {
            sendTransactionExceptionResponseToSsco(1, 1, "Cannot go back to sales");
        } else {
            sendTotalsResponse(sscoError);
        }
        getMessageHandler().getResponses().add(addEndResponse());
        getManager().resetTransactionalRewards();

        logger.debug("Exit");
    }
}
