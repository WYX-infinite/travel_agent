package com.wyx.travelagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wyx.travelagent.entity.User;
import com.wyx.travelagent.form.LoginForm;
import com.wyx.travelagent.service.UserService;
import com.wyx.travelagent.util.ResultUtil;
import com.wyx.travelagent.vo.ResultVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author wyx
 * @since 2026-08-28
 */
@RestController
public class UserController {
    private static final String CAPTCHA_KEY = "captcha";
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static Map<String, String> map = new HashMap<>();

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResultVO login(@RequestBody LoginForm loginForm) {
        User user = this.userService.getOne(new QueryWrapper<User>().eq("username", loginForm.getUsername()));
        if (user == null) return ResultUtil.error("用户名错误");
        if (!user.getPassword().equals(loginForm.getPassword())) return ResultUtil.error("密码错误");
        if (!map.get(CAPTCHA_KEY).equals(loginForm.getCaptcha())) return ResultUtil.error("验证码错误");
        return ResultUtil.success(user);
    }

    @GetMapping("/captcha")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setContentType("image/jpeg");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        Random random = new Random();

        g.setColor(getRandColor(200, 250));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setFont(new Font("Times New Roman", Font.ITALIC, 24));

        g.setColor(getRandColor(160, 200));
        for (int i = 0; i < 155; i++) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            int xl = random.nextInt(12);
            int yl = random.nextInt(12);
            g.drawLine(x, y, x + xl, y + yl);
        }

        String sRand = "";
        for (int i = 0; i < CODE_LENGTH; i++) {
            String rand = String.valueOf(random.nextInt(10));
            sRand += rand;
            g.setColor(new Color(20 + random.nextInt(110), 20 + random.nextInt(110), 20 + random.nextInt(110)));
            g.drawString(rand, 13 * i + 20, 28);
        }
        map.put(CAPTCHA_KEY, sRand);

        g.dispose();
        ServletOutputStream out = response.getOutputStream();
        ImageIO.write(image, "JPEG", out);
        out.flush();
        out.close();
    }

    private Color getRandColor(int fc, int bc) {
        Random random = new Random();
        if (fc > 255) fc = 255;
        if (bc > 255) bc = 255;
        int r = fc + random.nextInt(bc - fc);
        int g = fc + random.nextInt(bc - fc);
        int b = fc + random.nextInt(bc - fc);
        return new Color(r, g, b);
    }

}
