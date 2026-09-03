package com.wyx.travelagent.service.impl;

import com.wyx.travelagent.entity.User;
import com.wyx.travelagent.mapper.UserMapper;
import com.wyx.travelagent.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author wyx
 * @since 2026-08-28
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
