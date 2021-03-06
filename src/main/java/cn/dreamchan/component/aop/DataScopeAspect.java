package cn.dreamchan.component.aop;

import cn.dreamchan.common.base.BaseEntity;
import cn.dreamchan.common.constant.Constants;
import cn.dreamchan.common.enums.DataScopeEnum;
import cn.dreamchan.common.utils.SecurityUtils;
import cn.dreamchan.common.utils.StringUtils;
import cn.dreamchan.component.aop.annotation.DataScope;
import cn.dreamchan.component.security.pojo.LoginUserDetails;
import cn.dreamchan.modules.system.pojo.entity.RoleEntity;
import cn.dreamchan.modules.system.pojo.vo.UserVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 数据过滤处理
 *
 * @author DreamChan
 */
@Order(6)
@Aspect
@Component
public class DataScopeAspect {

    // 配置织入点
    @Pointcut("@annotation(cn.dreamchan.component.aop.annotation.DataScope)")
    public void dataScopePointCut() {

    }

    @Before("dataScopePointCut()")
    public void doBefore(JoinPoint point) {
        handleDataScope(point);
    }

    protected void handleDataScope(final JoinPoint joinPoint) {
        // 获得注解
        DataScope controllerDataScope = getAnnotationLog(joinPoint);
        if (controllerDataScope == null) {
            return;
        }
        // 获取当前的用户
        LoginUserDetails loginUser = SecurityUtils.getLoginUser();
        UserVo currentUser = loginUser.getUserVo();

        if (currentUser != null) {
            // 如果是超级管理员，则不过滤数据
            if (currentUser.getUserId() != Constants.ADMIN_USER_ID) {
                dataScopeFilter(joinPoint, currentUser, controllerDataScope.deptAlias(),
                        controllerDataScope.userAlias());
            }
        }
    }

    /**
     * 数据范围过滤
     *
     * @param joinPoint 切点
     * @param user      用户
     */
    public static void dataScopeFilter(JoinPoint joinPoint, UserVo user, String deptAlias, String userAlias) {
        StringBuilder sqlString = new StringBuilder();

        for (RoleEntity role : user.getRoles()) {
            String dataScope = role.getDataScope();
            if (DataScopeEnum.ALL.getCode().equals(dataScope)) {
                sqlString = new StringBuilder();
                break;
            } else if (DataScopeEnum.CUSTOM.getCode().equals(dataScope)) {
                sqlString.append(StringUtils.format(
                        " OR %s.dept_id IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = %s ) ", deptAlias,
                        role.getRoleId()));
            } else if (DataScopeEnum.DEPT.getCode().equals(dataScope)) {
                sqlString.append(StringUtils.format(" OR %s.dept_id = %s ", deptAlias, user.getDeptId()));
            } else if (DataScopeEnum.DEPT_AND_CHILD.getCode().equals(dataScope)) {
                sqlString.append(StringUtils.format(
                        " OR %s.dept_id IN ( SELECT dept_id FROM sys_dept WHERE dept_id = %s or find_in_set( %s , parent_ids ) )",
                        deptAlias, user.getDeptId(), user.getDeptId()));
            } else if (DataScopeEnum.SELF.getCode().equals(dataScope)) {
                if (StringUtils.isNotBlank(userAlias)) {
                    sqlString.append(StringUtils.format(" OR %s.user_id = %s ", userAlias, user.getUserId()));
                } else {
                    // 数据权限为仅本人且没有userAlias别名不查询任何数据
                    sqlString.append(" OR 1 = 0 ");
                }
            }
        }

        if (StringUtils.isNotBlank(sqlString.toString())) {
            QueryWrapper queryWrapper = (QueryWrapper) joinPoint.getArgs()[1];
            queryWrapper.apply(sqlString.substring(4));
        }
    }

    /**
     * 是否存在注解，如果存在就获取
     */
    private DataScope getAnnotationLog(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Method method = methodSignature.getMethod();

        if (method != null) {
            return method.getAnnotation(DataScope.class);
        }
        return null;
    }
}
