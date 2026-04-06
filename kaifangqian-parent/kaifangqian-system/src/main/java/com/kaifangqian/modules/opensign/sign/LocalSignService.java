package com.kaifangqian.modules.opensign.sign;

import com.kaifangqian.exception.PaasException;
import com.kaifangqian.modules.opensign.pdfbox.vo.CertificateInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalSignService {

    private static final String DEFAULT_ISSUER = "OpenSign Local Sign";

    @Value("${service.local-sign-enabled:false}")
    private Boolean localSignEnabled;

    @Value("${service.local-sign-pfx-path:}")
    private String localSignPfxPath;

    @Value("${service.local-sign-pfx-password:}")
    private String localSignPfxPassword;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(localSignEnabled);
    }

    public String getIssueOrg() {
        return DEFAULT_ISSUER;
    }

    public CertificateInfo loadCertificateInfo() {
        if (!isEnabled()) {
            throw new PaasException("本地证书签署未启用");
        }
        if (localSignPfxPath == null || localSignPfxPath.trim().isEmpty()) {
            throw new PaasException("本地证书签署PFX路径未配置");
        }
        if (localSignPfxPassword == null || localSignPfxPassword.trim().isEmpty()) {
            throw new PaasException("本地证书签署PFX密码未配置");
        }
        Path pfxPath = Paths.get(localSignPfxPath);
        if (!Files.exists(pfxPath)) {
            throw new PaasException("本地证书签署PFX文件不存在: " + localSignPfxPath);
        }
        try {
            CertificateInfo certInfo = new CertificateInfo();
            certInfo.setCert(Files.readAllBytes(pfxPath));
            certInfo.setPassword(localSignPfxPassword);
            certInfo.setCertType(CertificateInfo.CertTypeEnum.PKCS12);
            return certInfo;
        } catch (IOException e) {
            throw new PaasException("读取本地证书签署PFX失败");
        }
    }
}
