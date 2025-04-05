package com.sarvatra.rtsp.sbi.transaction;

import com.sarvatra.common.lang.SecureString;
import com.sarvatra.rtsp.ee.Customer;
import com.sarvatra.rtsp.ee.TokenTranlog;
import com.sarvatra.rtsp.sbi.kyc.GetAadharDetails;
import com.sarvatra.rtsp.sbi.kyc.UidOtpAuth;
import com.sarvatra.rtsp.transaction.TokenTxnSupport;
import org.apache.commons.lang3.StringUtils;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.transaction.Context;

public class GetAadharDetailsRequest extends TokenTxnSupport {
    private Configuration cfg;
    private String sourceId;
    private String url;
    private String uidPkPath;

    @Override
    protected int doPrepare(long id, Context ctx) throws Exception {
        TokenTranlog tl = ctx.get(TOKEN_TRANLOG);
        assertNotNull(tl, INVALID_CONTEXT, "Unable to get tranlog");

        Customer customer = ctx.get(CUSTOMER);
        assertNotNull(customer, INVALID_CONTEXT, "Unable to get customer");

        assertTrue(StringUtils.isNotBlank(customer.getVaultRefKey()), INVALID_CONTEXT, "Unable to find aadhar reference number");
        ctx.put(AADHAAR_VAULT_REF_KEY, customer.getVaultRefKey());

        String otpTranId = ctx.get(AADHAAR_OTP_TXN_ID);
        assertNotNull(otpTranId, INVALID_CONTEXT, "Unable to find otpTranId");

        SecureString aadharOtp = ctx.get(SECURE_OTP);
        assertNotNull(aadharOtp, INVALID_CONTEXT, "Unable to find aadharOtp");

        UidOtpAuth uidOtpAuth = new UidOtpAuth(aadharOtp.asString(),  uidPkPath, ctx.getLogEvent(), cfg );
        assertTrue(uidOtpAuth.process(),CBS_REQUEST_FAILED, "Error while processing Pid data" );

        GetAadharDetails verifyOtp = new GetAadharDetails(customer.getVaultRefKey(), uidOtpAuth, otpTranId, sourceId, url, ctx.getLogEvent(), cfg);
        verifyOtp.process();
        ctx.log("Response message " + verifyOtp.getMessage());
        assertTrue(verifyOtp.isRequestSuccess(), CBS_REQUEST_FAILED, "Generate otp request is failed");

        ctx.put(UID_DATA, verifyOtp.getUidData());

        tl.setCbsRefId(verifyOtp.getUrn());

        return PREPARED | NO_JOIN;
    }

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        super.setConfiguration(cfg);
        this.cfg = cfg;
        this.sourceId = cfg.get("source-id", "WE");
        this.url = cfg.get("url", "https://eisuat.sbi.co.in/gen5/gateway/dedupOn_bth/PANMobile");
        this.uidPkPath = cfg.get("uid-auth-public-key-path", "cfg/AuthStaging25082025.cer");

    }
}
