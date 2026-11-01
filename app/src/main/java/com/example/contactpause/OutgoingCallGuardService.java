package com.example.contactpause;

import android.net.Uri;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;

/** 사용자 동의로 CALL_REDIRECTION 역할을 받은 경우에만 동작. */
public class OutgoingCallGuardService extends CallRedirectionService {
    @Override
    public void onPlaceCall(Uri handle, PhoneAccountHandle initialPhoneAccount,
                            boolean allowInteractiveResponse) {
        // Android Telecom은 긴급전화(112,119 등)에 대해 이 콜백을 호출하지 않는다.
        // 데이터 연결을 이용하는 앱 통화는 여기서 다루지 않는다.
        if (LockStore.isLocked(this)) {
            cancelCall();
        } else {
            placeCallUnmodified();
        }
    }
}
