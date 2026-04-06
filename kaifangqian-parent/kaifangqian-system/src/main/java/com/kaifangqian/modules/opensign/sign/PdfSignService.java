/**
 * @description 文件签署服务
 *
 * Copyright (C) [2025] [版权所有者（北京资源律动科技有限公司）]. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * 注意：本代码基于 AGPLv3 协议发布。若通过网络提供服务（如 Web 应用），
 * 必须公开修改后的完整源代码（包括衍生作品），详见协议全文。
 */
package com.kaifangqian.modules.opensign.sign;

import com.kaifangqian.modules.account.enums.SignConsumeTypeEnum;
import com.kaifangqian.modules.opensign.pdfbox.AssinaturaPDF;
import com.kaifangqian.modules.opensign.enums.PersonalSignAuthTypeEnum;
import com.kaifangqian.modules.opensign.enums.SignTypeEnum;
import com.kaifangqian.modules.opensign.service.business.vo.YundunSignPositionArrayData;
import com.kaifangqian.exception.PaasException;
import com.kaifangqian.external.sign.request.AutoSignDocumentRequest;
import com.kaifangqian.external.sign.request.DocumentInfo;
import com.kaifangqian.external.sign.request.VerifySignDocumentRequest;
import com.kaifangqian.external.sign.response.AuthSignDocumentResponse;
import com.kaifangqian.external.sign.response.AutoSignDocumentResponse;
import com.kaifangqian.external.sign.service.SignServiceExternal;
import com.kaifangqian.modules.opensign.entity.SignRuDoc;
import com.kaifangqian.modules.opensign.service.business.PdfEncryptionService;
import com.kaifangqian.modules.opensign.service.business.vo.PdfboxSignData;
import com.kaifangqian.modules.opensign.service.business.vo.YundunSignPositionData;
import com.kaifangqian.modules.opensign.service.ru.SignRuDocService;
import com.kaifangqian.modules.opensign.service.tool.pojo.RealPositionProperty;
import com.kaifangqian.modules.opensign.utils.Base64;
import com.kaifangqian.modules.opensign.vo.base.sign.PdfSignResult;
import com.kaifangqian.pdfbox.AddExternalSignature;
import com.kaifangqian.pdfbox.AssinaturaPDF2;
import com.kaifangqian.pdfbox.vo.*;
import com.kaifangqian.utils.MyStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import com.kaifangqian.modules.opensign.vo.base.sign.PdfSignVoInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @Description: PdfSignService
 * @Package: com.kaifangqian.modules.opensign.pdfbox
 * @ClassName: PdfSignService
 * @author: yxb
 * @Date 2025/5/31
 */
@Slf4j
@Service
public class PdfSignService {

    @Autowired
    private SignServiceExternal signServiceExternal ;

    @Autowired
    private SignRuDocService signRuDocService ;

    @Autowired
    private PdfEncryptionService pdfEncryptionService ;

    @Autowired
    private LocalSignService localSignService;

    public Integer getPdfPage(byte[] pdfByte){
        Integer page = 0 ;

        try {
            PDDocument document = Loader.loadPDF(pdfByte);
            if (document == null) {
                throw new PaasException("pdf文件解析失败");
            }
            page = document.getNumberOfPages();
            document.close();
        } catch (Exception e) {
            throw new PaasException("pdf文件异常");
        }

        return page ;
    }

    /**
     * @Description #签署
     * @Param [pdfFile 文档, signByte 签章, certInfo 证书, positions 位置]
     * @return byte[]
     **/
    public PdfSignResult signWithYundunHash(PdfSignVoInfo pdfSignVoInfo){
//        log.info("开始签署了");
        //开始签署
        List<DocumentInfo> documentList = new ArrayList<>();
        VerifySignDocumentRequest verifySignDocumentRequest = null;
        AutoSignDocumentRequest autoSignDocumentRequest = null;

        //签署返回信息
        PdfSignResult pdfSignResult = new PdfSignResult();

        if(pdfSignVoInfo.getSignType().equals(SignTypeEnum.AUTH_SIGN.getCode())){
            verifySignDocumentRequest = new VerifySignDocumentRequest();
            verifySignDocumentRequest.setSeal(Base64.encode(pdfSignVoInfo.getEntSealByte()));
            verifySignDocumentRequest.setOrderNo(pdfSignVoInfo.getOrderNo());
        }else if(pdfSignVoInfo.getSignType().equals(SignTypeEnum.AUTO_SIGN.getCode())){
            autoSignDocumentRequest = new AutoSignDocumentRequest();
            autoSignDocumentRequest.setContractNo(pdfSignVoInfo.getSignRu().getId());
            autoSignDocumentRequest.setContractName(pdfSignVoInfo.getSignRu().getSubject());
            autoSignDocumentRequest.setUnionId(pdfSignVoInfo.getCertHolderTenantId());
            autoSignDocumentRequest.setSeal(Base64.encode(pdfSignVoInfo.getEntSealByte()));
            autoSignDocumentRequest.setBizId(pdfSignVoInfo.getTaskId());
            if(MyStringUtils.isNotBlank(pdfSignVoInfo.getPersonalSignAuthType()) && pdfSignVoInfo.getPersonalSignAuthType().equals(PersonalSignAuthTypeEnum.REQUIRED.getType())){
                autoSignDocumentRequest.setPersonalSignAuth(PersonalSignAuthTypeEnum.REQUIRED.getType());
            }else if(MyStringUtils.isBlank(pdfSignVoInfo.getPersonalSignAuthType())){
                autoSignDocumentRequest.setPersonalSignAuth(PersonalSignAuthTypeEnum.REQUIRED.getType());
            }else if(MyStringUtils.isNotBlank(pdfSignVoInfo.getPersonalSignAuthType()) && pdfSignVoInfo.getPersonalSignAuthType().equals(PersonalSignAuthTypeEnum.NOT_REQUIRED.getType())){
                autoSignDocumentRequest.setPersonalSignAuth(PersonalSignAuthTypeEnum.NOT_REQUIRED.getType());
            }
        }

        Map<String,PdfboxSignData> asssinaturePdfMap = new HashMap<String,PdfboxSignData>();
        //List<AssinaturaPDF2> assinaturas = new ArrayList<>();
        AssinaturaModel assinatura = null;

        //遍历每个文件，设置签署位置，执行签署
        for (Map.Entry<String, byte[]> entry : pdfSignVoInfo.getNewDocFileByteMap().entrySet()) {
            byte[] newDocFileByte = null;
            String docId = entry.getKey();
            SignRuDoc signRuDoc = signRuDocService.getById(docId);
            String docName = "";
            if(signRuDoc != null && MyStringUtils.isNotBlank(signRuDoc.getDocName())){
                docName = signRuDoc.getDocName();
            }

            byte[] docBytes = entry.getValue();

            //文件加密
            newDocFileByte = pdfEncryptionService.pdfToEncrypted(docBytes);

            //签署所需基础数据
            assinatura = new AssinaturaModel();
            assinatura.setLocation(pdfSignVoInfo.getAppName()+"："+pdfSignVoInfo.getAppId());
            if(MyStringUtils.isNotBlank(pdfSignVoInfo.getPersonalSignAuthType()) && pdfSignVoInfo.getPersonalSignAuthType().equals(PersonalSignAuthTypeEnum.REQUIRED.getType())){
                assinatura.setReason("ID:"+pdfSignVoInfo.getSignRu().getId()+"，依据电子签名法此电子签名与本人的签名/签章具有同等法律效力。");
            }else if(MyStringUtils.isBlank(pdfSignVoInfo.getPersonalSignAuthType())){
                assinatura.setReason("ID:"+pdfSignVoInfo.getSignRu().getId()+"，依据电子签名法此电子签名与本人的签名/签章具有同等法律效力。");
            }else if(MyStringUtils.isNotBlank(pdfSignVoInfo.getPersonalSignAuthType()) && pdfSignVoInfo.getPersonalSignAuthType().equals(PersonalSignAuthTypeEnum.NOT_REQUIRED.getType())){
                assinatura.setReason("ID:"+pdfSignVoInfo.getSignRu().getId()+"，该证书仅能保障文件在电子签名后不被篡改，不具备《电子签名法》所规定的法律效力。");
            }

            //文件
            assinatura.setPdf(newDocFileByte);
            //签章
            assinatura.setSignatureImage(pdfSignVoInfo.getEntSealByte());

            List<AssinaturaPosition> realPositions = new ArrayList<>();

            for(int i = 0 ; i < pdfSignVoInfo.getYundunSignPositionArrayDatas().size() ; i++){

                YundunSignPositionArrayData yundunSignPositionArrayData = pdfSignVoInfo.getYundunSignPositionArrayDatas().get(i);

                if(docId.equals(yundunSignPositionArrayData.getDocId())){
                    List<YundunSignPositionData> yundunSignPositionDataList = yundunSignPositionArrayData.getYundunSignPositionDataList();

                    for (YundunSignPositionData yundunSignPositionData : yundunSignPositionDataList){
                        RealPositionProperty realPositionProperty = yundunSignPositionData.getSealPosition();
                        byte[] sealImgByte = yundunSignPositionData.getSealImgByte();

                        AssinaturaPosition position = new AssinaturaPosition();
                        position.setPage(realPositionProperty.getPageNum());
                        position.setOffsetX(realPositionProperty.getStartx() + "");
                        position.setSignWidth((realPositionProperty.getEndx() - realPositionProperty.getStartx()) + "");
                        //纵坐标，pdfbox是从下向上计算的
                        float signHeight = realPositionProperty.getStarty() - realPositionProperty.getEndy();
                        if(signHeight < 0){
                            signHeight = realPositionProperty.getEndy() - realPositionProperty.getStarty() ;
                        }
                        position.setSignHeight(signHeight + "");
                        position.setOffsetY((realPositionProperty.getRealPdfHeight() - realPositionProperty.getStarty() - signHeight) + "");
                        position.setSeal(sealImgByte);
                        position.setFieldName(UUID.randomUUID().toString().replace("-", ""));

                        realPositions.add(position);
                    }
                    assinatura.setPositions(realPositions);
                }
            }
            try {
                AssinaturaPDF2 assinaturaPDF = new AssinaturaPDF2(assinatura, true);

                byte[] signedFile = assinaturaPDF.assina();
                PdfboxSignData pdfboxSignData = new PdfboxSignData();
                pdfboxSignData.setSignedFile(signedFile);

                if (assinaturaPDF.getLateExternalSigning()) {
                    LateExternalSignData signData = assinaturaPDF.getLateExternalSignData();
                    pdfboxSignData.setOffset(signData.getOffset());
                    asssinaturePdfMap.put(docId,pdfboxSignData);

                    // 构建云盾签署请求
                    DocumentInfo documentInfo = new DocumentInfo();
                    documentInfo.setDocumentId(docId);
                    documentInfo.setDocumentName(docName);
                    documentInfo.setDocumentHash(org.apache.pdfbox.util.Hex.getString((signData.getFileHash())));
                    documentList.add(documentInfo);

                }
            }catch (Exception e){
                e.printStackTrace();
                throw new PaasException("签署失败",e);
            }
            if(pdfSignVoInfo.getSignType().equals(SignTypeEnum.AUTH_SIGN.getCode())){
                verifySignDocumentRequest.setDocuments(documentList);
            }else if (pdfSignVoInfo.getSignType().equals(SignTypeEnum.AUTO_SIGN.getCode())){
                autoSignDocumentRequest.setDocuments(documentList);
            }
        }
        try {
            List<DocumentInfo> yundunDocumentList = null;
            Integer signType = null;
            String personalSignAuth = null;
            Integer authType = null;
            String responseMessage = null;
            if(pdfSignVoInfo.getSignType().equals(SignTypeEnum.AUTH_SIGN.getCode())){
                // 构建云盾意愿校验签署返回数据
                AuthSignDocumentResponse authSignDocumentResponse = null;
                // 返回云盾签署数据
                authSignDocumentResponse = signServiceExternal.submitAuthHashSign(verifySignDocumentRequest);
                responseMessage = authSignDocumentResponse.getResultMessage();
                yundunDocumentList = authSignDocumentResponse.getDocuments();

                if (authSignDocumentResponse.getSignType() != null){
                    signType = authSignDocumentResponse.getSignType();
                    personalSignAuth = authSignDocumentResponse.getPersonalSignAuth();
                    authType = authSignDocumentResponse.getAuthType();
                }

            }else if (pdfSignVoInfo.getSignType().equals(SignTypeEnum.AUTO_SIGN.getCode())){
                AutoSignDocumentResponse autoSignDocumentResponse = null;
                autoSignDocumentResponse = signServiceExternal.submitAutoHashSign(autoSignDocumentRequest);
                responseMessage = autoSignDocumentResponse.getResultMessage();
                yundunDocumentList = autoSignDocumentResponse.getDocuments();
                if (autoSignDocumentResponse.getSignType() != null){
                    signType = autoSignDocumentResponse.getSignType();
                    personalSignAuth = autoSignDocumentResponse.getPersonalSignAuth();
                    pdfSignResult.setSignOrderNo(autoSignDocumentResponse.getSignOrderNo());
                }
            }
            if(yundunDocumentList !=null && yundunDocumentList.size() > 0){
                for(DocumentInfo documentInfoTemp : yundunDocumentList){
                    PdfboxSignData pdfboxSignData = asssinaturePdfMap.get(documentInfoTemp.getDocumentId());
                    byte[] newPDF = AddExternalSignature.addSignature(pdfboxSignData.getSignedFile(), pdfboxSignData.getOffset(), Base64.decode(documentInfoTemp.getSignature()));
                    pdfSignVoInfo.getNewDocFileByteMap().put(documentInfoTemp.getDocumentId(),newPDF);
                }
            }else{
                log.error("签署失败",responseMessage);
                throw new PaasException(responseMessage);
            }
            pdfSignResult.setFinalSignType(signType);
            pdfSignResult.setPersonalSignAuth(personalSignAuth);
            pdfSignResult.setAuthType(authType);
        } catch (Exception e) {
            log.error("签署失败",e);
            throw new PaasException(e.getMessage());
        }

        pdfSignResult.setNewDocFileByteMap(pdfSignVoInfo.getNewDocFileByteMap());
//        log.info("签署完成了");
        return pdfSignResult ;
    }

    public PdfSignResult signWithLocalCert(PdfSignVoInfo pdfSignVoInfo) {
        com.kaifangqian.modules.opensign.pdfbox.vo.CertificateInfo certInfo = localSignService.loadCertificateInfo();
        PdfSignResult pdfSignResult = new PdfSignResult();
        Map<String, byte[]> signedDocMap = new HashMap<>();

        for (Map.Entry<String, byte[]> entry : pdfSignVoInfo.getNewDocFileByteMap().entrySet()) {
            String docId = entry.getKey();
            byte[] currentPdf = pdfEncryptionService.pdfToEncrypted(entry.getValue());

            for (YundunSignPositionArrayData positionArrayData : pdfSignVoInfo.getYundunSignPositionArrayDatas()) {
                if (!docId.equals(positionArrayData.getDocId()) || positionArrayData.getYundunSignPositionDataList() == null) {
                    continue;
                }
                for (YundunSignPositionData positionData : positionArrayData.getYundunSignPositionDataList()) {
                    currentPdf = signSinglePosition(currentPdf, certInfo, positionData.getSealImgByte(),
                            positionData.getSealPosition(), pdfSignVoInfo);
                }
            }
            signedDocMap.put(docId, currentPdf);
        }

        pdfSignResult.setNewDocFileByteMap(signedDocMap);
        pdfSignResult.setFinalSignType(SignConsumeTypeEnum.WILLINGESS_FREE_SIGN.getCode());
        pdfSignResult.setAuthType(SignConsumeTypeEnum.WILLINGESS_FREE_SIGN.getCode());
        if (MyStringUtils.isNotBlank(pdfSignVoInfo.getPersonalSignAuthType())) {
            pdfSignResult.setPersonalSignAuth(pdfSignVoInfo.getPersonalSignAuthType());
        } else {
            pdfSignResult.setPersonalSignAuth(PersonalSignAuthTypeEnum.REQUIRED.getType());
        }
        return pdfSignResult;
    }

    private byte[] signSinglePosition(byte[] pdfBytes,
                                      com.kaifangqian.modules.opensign.pdfbox.vo.CertificateInfo certInfo,
                                      byte[] sealBytes,
                                      RealPositionProperty realPositionProperty,
                                      PdfSignVoInfo pdfSignVoInfo) {
        try {
            com.kaifangqian.modules.opensign.pdfbox.vo.AssinaturaModel assinatura =
                    new com.kaifangqian.modules.opensign.pdfbox.vo.AssinaturaModel();
            assinatura.setCertInfo(certInfo);
            assinatura.setPdf(pdfBytes);
            assinatura.setSignatureImage(sealBytes);
            assinatura.setName(pdfSignVoInfo.getAppName());
            assinatura.setLocation(pdfSignVoInfo.getAppName() + "：" + pdfSignVoInfo.getAppId());

            if (MyStringUtils.isNotBlank(pdfSignVoInfo.getPersonalSignAuthType())
                    && pdfSignVoInfo.getPersonalSignAuthType().equals(PersonalSignAuthTypeEnum.NOT_REQUIRED.getType())) {
                assinatura.setReason("ID:" + pdfSignVoInfo.getSignRu().getId() + "，本地证书签署，仅用于私有化环境测试。");
            } else {
                assinatura.setReason("ID:" + pdfSignVoInfo.getSignRu().getId() + "，本地证书签署，签署人已在本地模式下完成确认。");
            }

            com.kaifangqian.modules.opensign.pdfbox.vo.AssinaturaPosition position =
                    new com.kaifangqian.modules.opensign.pdfbox.vo.AssinaturaPosition();
            position.setPage(realPositionProperty.getPageNum());
            position.setOffsetX(String.valueOf(realPositionProperty.getStartx()));
            position.setSignWidth(String.valueOf(realPositionProperty.getEndx() - realPositionProperty.getStartx()));
            float signHeight = realPositionProperty.getStarty() - realPositionProperty.getEndy();
            if (signHeight < 0) {
                signHeight = realPositionProperty.getEndy() - realPositionProperty.getStarty();
            }
            position.setSignHeight(String.valueOf(signHeight));
            position.setOffsetY(String.valueOf(realPositionProperty.getRealPdfHeight() - realPositionProperty.getStarty() - signHeight));
            assinatura.setPosition(position);
            assinatura.setSignatureKey(UUID.randomUUID().toString().replace("-", ""));

            AssinaturaPDF assinaturaPDF = new AssinaturaPDF(assinatura);
            return assinaturaPDF.assina();
        } catch (Exception e) {
            log.error("本地证书签署失败", e);
            throw new PaasException("本地证书签署失败");
        }
    }
}
