package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 文件上传
     * @param file 前端传过来的文件对象
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：{}", file);

        try {
            // 1. 获取原始文件名 (例如: 烤烤鸭.jpg)
            String originalFilename = file.getOriginalFilename();

            // 2. 截取文件的后缀名 (例如: .jpg)
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

            // 3. 构造新文件名称，防止重名覆盖 (利用 UUID 生成唯一名字)
            String objectName = UUID.randomUUID().toString() + extension;

            // 4. 调用阿里云工具类进行上传，并获取返回的图片真实访问网址
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);

            // 5. 把网址返回给前端
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("文件上传失败：{}", e);
        }

        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}