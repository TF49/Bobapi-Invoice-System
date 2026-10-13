package com.invoice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaChallengeResponse {
    private String captchaId;
    private String masterImage;
    private String tileImage;
    private int tileWidth;
    private int tileHeight;
    private int masterWidth;
    private int masterHeight;
    private int thumbDisplayX;
    private int thumbDisplayY;
}
