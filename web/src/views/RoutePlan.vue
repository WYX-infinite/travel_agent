<template>
  <div class="container">
    <!-- 查询区域 -->
    <el-card class="search-main-card">
      <div class="header">
        <h1>
          <el-icon class="header-icon"><MagicStick /></el-icon>
          智能路线规划
        </h1>
        <p>输入您的出行需求，获取最优路线方案</p>
      </div>

      <el-form ref="searchForm" :model="searchForm" :rules="rules" label-width="100px" class="search-form">
        <!-- 第一行 -->
        <el-row :gutter="24">
          <!-- 出发地 -->
          <el-col :span="8">
            <el-form-item label="出发地" prop="departurePlace">
              <el-input
                  v-model="searchForm.departurePlace"
                  placeholder="请输入出发地（如：上海）"
                  clearable
                  :class="{'input-focus':searchForm.departurePlace}"
              />
            </el-form-item>
          </el-col>

          <!-- 目的地 -->
          <el-col :span="8">
            <el-form-item label="目的地" prop="destination">
              <el-input
                  v-model="searchForm.destination"
                  placeholder="请输入目的地（如：广州）"
                  clearable
                  :class="{'input-focus':searchForm.destination}"
              />
            </el-form-item>
          </el-col>

          <!-- 优先级 -->
          <el-col :span="8">
            <el-form-item label="优先级" prop="userPriority">
              <el-select
                  v-model="searchForm.userPriority"
                  placeholder="请选择优先级"
                  style="width: 100%"
              >
                <el-option label="价格优先" value="价格优先"/>
                <el-option label="时间优先" value="时间优先"/>
                <el-option label="舒适优先" value="舒适优先"/>
                <el-option label="平衡" value="平衡"/>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 备注 -->
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注信息" prop="remark">
              <el-input
                  v-model="searchForm.remark"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入预算范围、时间窗口等约束条件（可选）"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 按钮 -->
        <el-row>
          <el-col :span="24" class="button-group">
            <el-button
                type="primary"
                size="large"
                class="search-button"
                :loading="loading"
                @click="submitQuery"
            >
              <el-icon><Search /></el-icon>
              <span>查询路线方案</span>
            </el-button>

            <el-button size="large" class="reset-button" :disabled="loading" @click="resetForm">
              <el-icon><Refresh /></el-icon>
              <span>重置</span>
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 查询结果 -->
    <el-card v-if="queryResult" class="result-card">
      <!-- Vue3 / Element Plus header 插槽 -->
      <template #header>
        <div class="result-header">
          <h2>
            {{ queryResult.departurePlace }} → {{ queryResult.destination }}
          </h2>
          <span class="result-date">{{ currentDate }}</span>
        </div>
      </template>

      <!-- 推荐方案 -->
      <div v-if="schemes && schemes.length > 0">
        <h3 class="section-title">
          推荐方案
        </h3>

        <div class="scheme-list-container">
          <div
              v-for="(scheme, index) in schemes"
              :key="index"
              class="scheme-item"
              :class="{'first-scheme':index === 0}"
          >
            <!-- 卡片头部 -->
            <div class="scheme-header">
              <span class="scheme-index">
                {{ index + 1 }}
              </span>
              <span class="route-type">
                {{scheme.route || "综合路线" }}
              </span>
              <span v-if="index === 0" class="scheme-tag">最佳推荐</span>
            </div>
            <!-- 卡片内容 -->
            <div class="scheme-content">
              <div v-for="(value, key) in scheme" :key="key" class="field-wrapper">
                <!-- route 和 segments 不直接显示 -->
                <div v-if="!['route','segments'].includes(key)" class="field-item">
                  <!-- 特殊字段 -->
                  <div v-if="isSpecialField(key)" class="special-field">
                    <!-- 总费用 -->
                    <span v-if="key === 'totalCost'" class="route-cost">
                      💰{{ formatKey(key) }}：{{ value }}
                    </span>

                    <!-- 总时间 -->
                    <span v-else-if="key === 'totalTime'" class="route-time">
                      ⏱️{{ formatKey(key) }}：{{ value }}
                    </span>

                    <!-- 舒适度 -->
                    <span v-else-if="key ==='comfortLevel'" class="comfort-level" :class="getComfortClass(value)">
                      {{ formatKey(key) }}：{{ value }}
                    </span>

                    <!-- 换乘次数 -->
                    <span v-else-if="key ==='transferTimes' ||key ==='transferCount'" class="transfer-count">
                      {{ formatKey(key) }}：{{ value }}
                    </span>

                    <!-- 其他特殊字段 -->
                    <span v-else>
                      <strong>{{ formatKey(key) }}：</strong>{{ value }}
                    </span>
                  </div>

                  <!-- 普通字段 -->
                  <div v-else class="regular-field">
                    <strong>{{ formatKey(key) }}：</strong>{{ value }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 出行建议 -->
      <div v-if="queryResult.suggestion" class="suggestion-box">
        <h3 class="suggestion-title">💡 出行建议</h3>
        <p>{{ queryResult.suggestion }}</p>
      </div>
    </el-card>

    <!-- 无结果 -->
    <el-card v-if="!queryResult &&!loading &&hasSearched" class="empty-card">
      <div class="empty-state">
        <el-icon class="empty-icon">
          <InfoFilled />
        </el-icon>

        <p>暂无查询结果，请检查输入信息后重试</p>
      </div>
    </el-card>
  </div>
</template>


<script>
import axios from "axios";

import {
  InfoFilled,
  MagicStick,
  Refresh,
  Search
} from "@element-plus/icons-vue";

import {
  ElMessage
} from "element-plus";

export default {
  name: "RoutePlan",

  components: {
    InfoFilled,
    MagicStick,
    Refresh,
    Search
  },

  data() {
    return {
      /*
       * 是否正在查询
       */
      loading: false,
      /*
       * 用户是否执行过查询
       */
      hasSearched: false,
      /*
       * 查询表单
       */
      searchForm: {
        departurePlace: "上海",
        destination: "广州",
        userPriority: "平衡",
        remark: ""
      },

      /*
       * 表单验证规则
       */
      rules: {
        departurePlace: [
          {
            required: true,
            message: "请输入出发地",
            trigger: "blur"
          }
        ],

        destination: [
          {
            required: true,
            message: "请输入目的地",
            trigger: "blur"
          }
        ],

        userPriority: [
          {
            required: true,
            message: "请选择优先级",
            trigger: "change"
          }
        ]
      },

      /*
       * 后端查询结果
       */
      queryResult: null,
      /*
       * 推荐方案列表
       */
      schemes: []
    };
  },

  computed: {
    /*
     * 当前日期
     */
    currentDate() {
      return new Date().toLocaleDateString(
          "zh-CN",
          {
            year: "numeric",
            month: "2-digit",
            day: "2-digit"
          }
      );
    }
  },

  methods: {
    /*
     * 查询路线
     */
    async submitQuery() {
      const form = this.$refs.searchForm;
      if (!form) return;
      /*
       * 等待 Element Plus 表单验证
       */
      const valid = await form.validate().catch(() => false);
      if (!valid) {
        ElMessage.warning("请完善必填信息");
        return;
      }
      this.loading = true;
      this.hasSearched = true;
      /*
       * 每次新查询前清空旧数据
       */
      this.queryResult = null;
      this.schemes = [];

      /*
       * 生成发送给后端的自然语言
       */
      let userMessage = `我想从${this.searchForm.departurePlace}` + `到${this.searchForm.destination}`;
      if (this.searchForm.userPriority) userMessage += `，优先级是${this.searchForm.userPriority}`;
      if (this.searchForm.remark) userMessage += `，备注：${this.searchForm.remark}`;

      try {
        /*
         * 请求后端 使用 params，axios 会负责 query 参数编码
         */
        const response =
            await axios.get(
                "http://localhost:8181/route",
                {
                  params: {
                    msg: userMessage
                  },
                  timeout: 60000
                }
            );

        this.queryResult = response.data;
        /*
         * 解析推荐方案
         */
        if (this.queryResult && this.queryResult.schemes) {
          try {
            /*
             * 后端如果已经返回数组
             */
            if (Array.isArray(this.queryResult.schemes)) {
              this.schemes = this.queryResult.schemes;
            }
            /*
             * 后端返回 JSON 字符串
             */
            else if (typeof this.queryResult.schemes === "string") {
              const parsedSchemes = JSON.parse(this.queryResult.schemes);
              this.schemes = Array.isArray(parsedSchemes) ? parsedSchemes : [parsedSchemes];
            }
            /*
             * 后端直接返回对象
             */
            else {
              this.schemes = [this.queryResult.schemes];
            }
          } catch (error) {
            console.error("解析方案数据失败:", error);
            this.schemes = [];
            ElMessage.error("方案数据格式异常，请重试");
            return;
          }
        }
        ElMessage.success("查询成功！");
      } catch (error) {
        console.error("查询路线失败:", error);
        this.queryResult = null;
        this.schemes = [];
        if(error.code === "ECONNABORTED"){
          ElMessage.error("AI路线规划超时，请缩小查询范围后重试");
        }else{
          ElMessage.error("查询失败，请查看后端错误日志");
        }
      } finally {
        this.loading = false;
      }
    },

    /*
     * 重置表单
     */
    resetForm() {
      const form = this.$refs.searchForm;
      if (form) {
        form.resetFields();
      }
      this.queryResult = null;
      this.schemes = [];
      this.hasSearched = false;
    },
    /*
     * 字段英文 -> 中文
     */
    formatKey(key) {
      const keyMap = {
        planId: "方案规划ID",
        departurePlace: "出发地",
        destination: "目的地",
        userPriority: "用户优先级",
        remark: "备注信息",
        schemes: "推荐方案列表",
        suggestion: "出行建议",
        createTime: "生成时间",
        schemeId: "方案ID",
        trafficCombination: "交通组合",
        totalTime: "总时长",
        transferWaitTime: "中转等待时间",
        totalCost: "总费用",
        additionalCost: "附加费用",
        comfortLevel: "舒适指数",
        transferTimes: "中转次数",
        departureTime: "出发时间",
        arrivalTime: "到达时间",
        route: "路线类型",
        description: "方案描述",
        transferCount: "换乘次数",
        distance: "总距离",
        carbonEmission: "碳排放",
        segments: "行程分段",
        price: "价格",
        duration: "时长",
        transport: "交通方式",
        tollFee: "过路费",
        departure: "出发地",
        arrival: "到达地",
        station: "车站",
        frequency: "班次频率",
        seatClass: "座位等级",
        discount: "折扣信息",
        reliability: "可靠性",
        weatherImpact: "天气影响"
      };

      return (
          keyMap[key] || key.replace(/([A-Z])/g, " $1").replace(/^./, (match) => match.toUpperCase()).replace(/_/g, " ").trim()
      );
    },
    /*
     * 判断是否特殊字段
     */
    isSpecialField(key) {
      return [
        "totalCost",
        "totalTime",
        "comfortLevel",
        "transferTimes",
        "transferCount",
        "additionalCost"
      ].includes(key);
    },

    /*
     * 根据舒适度返回 CSS class
     */
    getComfortClass(level) {
      if (!level) {
        return "";
      }
      const value = String(level);
      const levelStr = value.toLowerCase();
      /*
       * 高舒适度
       */
      if (value.includes("高") || value.includes("商务座") || value.includes("头等舱") || value.includes("直飞") ||
          levelStr.includes("high") || levelStr.includes("business")) {
        return "comfort-high";
      }

      /*
       * 中等舒适度
       */
      if (
          value.includes("中") || value.includes("二等座") || value.includes("经济舱") ||
          levelStr.includes("medium") || levelStr.includes("economy")) {
        return "comfort-medium";
      }

      /*
       * 低舒适度
       */
      if (value.includes("低") || value.includes("硬座") || value.includes("无座") ||
          levelStr.includes("low") || levelStr.includes("hard")) {
        return "comfort-low";
      }
      return "";
    }
  }
};
</script>


<style scoped>

/*
 * scoped 样式下，
 * body 需要使用 :global
 */
:global(body) {
  margin: 0;

  padding: 0;

  background-color:
      #f7f8fa;

  font-family:
      -apple-system,
      BlinkMacSystemFont,
      "Segoe UI",
      Roboto,
      "Helvetica Neue",
      Arial,
      sans-serif;
}


/* ==========================
   页面容器
   ========================== */

.container {
  width: 100% !important;

  min-width: 100%;

  margin:
      70px auto 0;

  box-sizing:
      border-box;

  padding:
      0 20px;
}


/* ==========================
   查询卡片
   ========================== */

.search-main-card {
  background:
      #ffffff;

  border-radius:
      16px;

  box-shadow:
      0 4px 12px
      rgba(
          0,
          0,
          0,
          0.05
      );

  padding:
      32px;

  margin-bottom:
      24px;

  border:
      1px solid #f5f7fa;

  width:
      100%;

  box-sizing:
      border-box;
}


/* ==========================
   标题
   ========================== */

.header {
  text-align:
      center;

  margin-bottom:
      28px;

  padding-bottom:
      20px;

  border-bottom:
      1px solid #f0f2f5;

  color:
      #1d2129;

  width:
      100%;
}


.header h1 {
  font-size:
      28px;

  font-weight:
      700;

  margin-bottom:
      8px;

  color:
      #007bff;

  display:
      inline-flex;

  align-items:
      center;

  gap:
      12px;
}


.header-icon {
  font-size:
      32px;

  color:
      #007bff;

  transition:
      all 0.3s ease;
}


.header:hover
.header-icon {
  transform:
      rotate(15deg);

  color:
      #0056b3;
}


.header p {
  font-size:
      16px;

  color:
      #667085;

  margin:
      0;

  line-height:
      1.5;
}


/* ==========================
   查询表单
   ========================== */

.search-form {
  width:
      100%;
}


/* ==========================
   结果卡片
   ========================== */

.result-card,
.empty-card {
  background:
      #ffffff;

  border-radius:
      16px;

  box-shadow:
      0 4px 12px
      rgba(
          0,
          0,
          0,
          0.05
      );

  padding:
      24px;

  margin-bottom:
      24px;

  border:
      1px solid #f5f7fa;

  width:
      100%;

  box-sizing:
      border-box;
}


/* ==========================
   结果头部
   ========================== */

.result-header {
  display:
      flex;

  justify-content:
      space-between;

  align-items:
      center;

  width:
      100%;
}


.result-header h2 {
  font-size:
      20px;

  font-weight:
      600;

  color:
      #1d2129;

  margin:
      0;
}


.result-date {
  font-size:
      14px;

  color:
      #86909c;
}


/* ==========================
   按钮
   ========================== */

.button-group {
  display:
      flex;

  justify-content:
      center;

  gap:
      16px;

  margin-top:
      12px;

  width:
      100%;
}


.search-button {
  background:
      #007bff;

  border-color:
      #007bff;

  padding:
      12px 32px;

  font-size:
      16px;

  font-weight:
      500;

  border-radius:
      8px;

  transition:
      all 0.3s ease;
}


.search-button:hover {
  background:
      #0056b3;

  border-color:
      #0056b3;

  transform:
      translateY(-2px);

  box-shadow:
      0 4px 12px
      rgba(
          0,
          123,
          255,
          0.2
      );
}


.reset-button {
  background:
      #f2f3f5;

  border-color:
      #f2f3f5;

  color:
      #4e5969;

  padding:
      12px 32px;

  font-size:
      16px;

  font-weight:
      500;

  border-radius:
      8px;

  transition:
      all 0.3s ease;
}


.reset-button:hover {
  background:
      #e5e6eb;

  border-color:
      #e5e6eb;

  color:
      #1d2129;
}


.reset-button:disabled {
  background:
      #f8f9fa;

  color:
      #c9cdd4;

  border-color:
      #e9ecef;

  cursor:
      not-allowed;
}


/* ==========================
   推荐方案标题
   ========================== */

.section-title {
  font-size:
      18px;

  font-weight:
      600;

  color:
      #1d2129;

  margin-bottom:
      20px;

  padding-bottom:
      8px;

  border-bottom:
      2px solid #007bff;

  display:
      block;

  width:
      100%;
}


/* ==========================
   一行三个方案
   ========================== */

.scheme-list-container {
  display:
      flex;

  flex-wrap:
      wrap;

  gap:
      15px;

  width:
      100%;
}


.scheme-item {
  flex:
      1;

  min-width:
      calc(
          33.333% - 12px
      );

  max-width:
      calc(
          33.333% - 12px
      );

  border-radius:
      12px;

  padding:
      18px;

  margin-bottom:
      0 !important;

  background:
      #f7f8fa;

  border:
      1px solid #f0f0f0;

  transition:
      all 0.3s ease;

  box-sizing:
      border-box;

  text-align:
      left !important;
}


.scheme-item:hover {
  transform:
      translateY(-4px);

  box-shadow:
      0 8px 24px
      rgba(
          0,
          0,
          0,
          0.08
      );
}


.first-scheme {
  border:
      2px solid #007bff;

  background:
      linear-gradient(
          135deg,
          rgba(
              0,
              123,
              255,
              0.05
          )
          0%,
          rgba(
              0,
              123,
              255,
              0
          )
          100%
      );
}


/* ==========================
   方案头部
   ========================== */

.scheme-header {
  display:
      flex;

  justify-content:
      flex-start;

  align-items:
      center;

  margin-bottom:
      14px;

  padding-bottom:
      10px;

  border-bottom:
      1px solid #f0f0f0;

  width:
      100%;

  gap:
      8px;
}


.scheme-index {
  width:
      28px;

  height:
      28px;

  background:
      #007bff;

  color:
      #ffffff;

  border-radius:
      50%;

  font-size:
      14px;

  font-weight:
      600;

  display:
      inline-flex;

  align-items:
      center;

  justify-content:
      center;

  flex-shrink:
      0;
}


.route-type {
  font-weight:
      600;

  font-size:
      15px;

  color:
      #1d2129;

  text-align:
      left !important;

  flex:
      none;

  margin:
      0 8px;
}


.scheme-tag {
  background:
      #007bff;

  color:
      #ffffff;

  padding:
      3px 10px;

  border-radius:
      20px;

  font-size:
      15px;

  font-weight:
      500;

  margin-left:
      auto;
}


/* ==========================
   方案内容
   ========================== */

.scheme-content {
  margin-top:
      8px;

  width:
      100%;

  text-align:
      left !important;
}


.field-wrapper {
  margin-bottom:
      8px;

  width:
      100%;

  text-align:
      left !important;
}


.field-item {
  display:
      flex;

  align-items:
      center;

  padding:
      4px 0;

  flex-wrap:
      wrap;

  font-size:
      13px;

  text-align:
      left !important;
}


.special-field {
  display:
      flex;

  align-items:
      center;

  width:
      100%;

  margin-right:
      0 !important;

  margin-bottom:
      4px;

  text-align:
      left !important;
}


.regular-field {
  font-size:
      13px;

  color:
      #4e5969;

  width:
      100%;

  text-align:
      left !important;
}


/* ==========================
   特殊字段
   ========================== */

.route-cost {
  color:
      #e53e3e;

  font-weight:
      500;
}


.route-time {
  color:
      #38a169;

  font-weight:
      500;
}


.comfort-level {
  display:
      inline-flex;

  align-items:
      center;

  justify-content:
      center;

  padding:
      2px 8px;

  border-radius:
      12px;

  font-size:
      11px;

  font-weight:
      500;

  color:
      #ffffff;

  margin-right:
      0;
}


.comfort-high {
  background-color:
      #38a169;
}


.comfort-medium {
  background-color:
      #ed8936;
}


.comfort-low {
  background-color:
      #e5e6eb;

  color:
      #666;
}


.transfer-count {
  color:
      #718096;
}


/* ==========================
   出行建议
   ========================== */

.suggestion-box {
  background:
      linear-gradient(
          135deg,
          #e8f4f8 0%,
          #f0f8fb 100%
      );

  border-left:
      4px solid #007bff;

  padding:
      16px;

  margin-top:
      24px;

  border-radius:
      8px;

  width:
      100%;

  box-sizing:
      border-box;

  text-align:
      left;
}


.suggestion-title {
  font-size:
      16px;

  font-weight:
      600;

  color:
      #1d2129;

  margin-bottom:
      8px;
}


.suggestion-box p {
  color:
      #4e5969;

  line-height:
      1.6;

  margin:
      0;

  font-size:
      14px;
}


/* ==========================
   空状态
   ========================== */

.empty-state {
  text-align:
      center;

  padding:
      60px 20px;

  color:
      #86909c;

  width:
      100%;
}


.empty-icon {
  color:
      #c9cdd4;

  margin-bottom:
      16px;

  font-size:
      48px;
}


.empty-state p {
  font-size:
      16px;

  margin:
      0;
}


/* ==========================
   Element Plus
   scoped 内部样式
   ========================== */

:deep(
  .el-form-item__label
) {
  font-weight:
      500;

  color:
      #4e5969;
}


:deep(
  .el-input__wrapper
) {
  border-radius:
      8px;

  transition:
      all 0.3s ease;
}


:deep(
  .el-textarea__inner
) {
  border-radius:
      8px;

  transition:
      all 0.3s ease;
}


.input-focus
:deep(
  .el-input__wrapper
) {
  box-shadow:
      0 0 0 1px
      #007bff inset,
      0 0 0 2px
      rgba(
          0,
          123,
          255,
          0.15
      );
}


/* ==========================
   移动端
   ========================== */

@media (
max-width:
    768px
) {

  .container {
    padding:
        10px !important;

    margin-top:
        50px;
  }


  .search-main-card {
    padding:
        20px 15px;
  }


  .header h1 {
    font-size:
        24px;
  }


  .header-icon {
    font-size:
        28px;
  }


  :deep(
    .el-col
  ) {
    width:
        100% !important;

    max-width:
        100% !important;

    flex:
        0 0 100% !important;

    margin-bottom:
        16px;
  }


  .button-group {
    flex-direction:
        column;

    gap:
        8px;

    padding:
        0;
  }


  .scheme-item {
    min-width:
        100% !important;

    max-width:
        100% !important;

    margin-bottom:
        10px !important;
  }


  .scheme-header {
    flex-direction:
        row;

    align-items:
        center;

    gap:
        8px;
  }
}

</style>