<template>
  <div class="travel-recommendation-app">
    <div class="main-container">
      <div class="form-container">
        <el-form ref="preferenceForm" :model="userPreferences" label-width="160px" class="preference-form" :rules="formRules">
          <div class="form-section">
            <h2 class="section-title">
              <el-icon><User /></el-icon>
              基本旅行信息
            </h2>

            <el-form-item label="出发地" prop="departureCity">
              <el-input v-model="userPreferences.departureCity" placeholder="请输入您的出发地，例如：浙江杭州" clearable/>
            </el-form-item>

            <el-form-item label="目的地类型" prop="destinationType">
              <el-select v-model="userPreferences.destinationType" placeholder="请选择您喜欢的目的地类型" style="width: 100%">
                <el-option label="自然景观" value="自然景观" />
                <el-option label="城市人文" value="城市人文" />
                <el-option label="海滨度假" value="海滨度假" />
                <el-option label="历史文化" value="历史文化" />
                <el-option label="乡村田园" value="乡村田园" />
                <el-option label="主题乐园" value="主题乐园" />
                <el-option label="购物城市" value="购物城市" />
                <el-option label="探险户外" value="探险户外" />
              </el-select>
            </el-form-item>

            <el-form-item label="预算范围" prop="budgetRange">
              <el-select v-model="userPreferences.budgetRange" placeholder="请选择您的预算范围" style="width: 100%">
                <el-option label="经济型 (人均1000元以下)" value="经济型"/>
                <el-option label="舒适型 (人均1000-3000元)" value="舒适型"/>
                <el-option label="豪华型 (人均3000元以上)" value="豪华型"/>
              </el-select>
            </el-form-item>

            <el-form-item label="出行时间" prop="travelTime">
              <el-input v-model="userPreferences.travelTime" placeholder="例如：夏季、国庆假期、3天2晚、下周末" clearable/>
            </el-form-item>

            <el-form-item label="同行人类型" prop="companionType">
              <el-select v-model="userPreferences.companionType" placeholder="请选择同行人类型" style="width: 100%">
                <el-option label="独自旅行" value="独自旅行" />
                <el-option label="情侣/夫妻" value="情侣" />
                <el-option label="家庭亲子" value="家庭亲子" />
                <el-option label="朋友结伴" value="朋友结伴" />
                <el-option label="公司团建" value="公司团建" />
              </el-select>
            </el-form-item>
          </div>

          <div class="form-section">
            <h2 class="section-title">
              <el-icon><Setting /></el-icon>
              详细偏好设置
            </h2>

            <el-form-item label="活动偏好" prop="activityPref" style="text-align: left !important">
              <el-checkbox-group v-model="userPreferences.activityPrefArray" style="text-align: left !important; width: 100%" @change="updateActivityPref">
                <el-checkbox value="美食">美食</el-checkbox>
                <el-checkbox value="徒步登山">徒步登山</el-checkbox>
                <el-checkbox value="文化体验">文化体验</el-checkbox>
                <el-checkbox value="水上活动">水上活动</el-checkbox>
                <el-checkbox value="购物血拼">购物血拼</el-checkbox>
                <el-checkbox value="休闲放松">休闲放松</el-checkbox>
                <el-checkbox value="夜生活">夜生活</el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="住宿偏好" prop="accommodationPref" style="text-align: left !important">
              <el-radio-group
                  v-model="userPreferences.accommodationPref"
                  style="
                  text-align: left !important;
                  width: 100%;
                  display: block;
                  margin-top: 12px;
                "
              >
                <el-radio value="经济酒店">经济酒店</el-radio>
                <el-radio value="星级酒店">星级酒店</el-radio>
                <el-radio value="特色民宿">特色民宿</el-radio>
                <el-radio style="margin-top: 16px" value="青年旅舍">青年旅舍</el-radio>
                <el-radio value="度假村">度假村</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item
                label="交通方式"
                prop="transportation"
                style="text-align: left !important"
            >
              <el-radio-group
                  v-model="userPreferences.transportation"
                  style="width: 100%;text-align: left !important;display: block;margin-top: 12px;"
              >
                <el-radio value="飞机">飞机</el-radio>
                <el-radio value="高铁">高铁</el-radio>
                <el-radio value="自驾">自驾</el-radio>
                <el-radio value="火车">火车</el-radio>
                <el-radio value="大巴">大巴</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="特殊要求">
              <el-input
                  v-model="userPreferences.specialRequire"
                  type="textarea"
                  placeholder="请输入您的特殊要求，如：宠物友好、无障碍设施、适合老人、儿童设施等"
                  :rows="3"
                  maxlength="255"
                  show-word-limit
              />
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button type="primary" size="large" :loading="submitLoading" @click="submitPreferences">
              <el-icon><Promotion /></el-icon>
              <span>获取AI推荐</span>
            </el-button>

            <el-button size="large" :disabled="!streamCompleted" @click="resetForm">
              <el-icon><Refresh /></el-icon>
              <span>重置表单</span>
            </el-button>

            <div v-if="userMessagePreview" class="form-tips">
              <p><strong>将发送给AI的内容预览：</strong></p>
              <div class="preview-content">{{ userMessagePreview }}</div>
            </div>
          </div>
        </el-form>
      </div>

      <div ref="resultScrollContainer" v-loading="loading" class="result-container">
        <div v-if="!streamResult" class="empty-result">
          <div class="empty-icon">
            <el-icon><Flag /></el-icon>
          </div>
          <h3>等待AI专属推荐</h3>
          <p>填写左侧旅行偏好，点击「获取AI推荐」，为您生成精准旅行方案</p>
        </div>

        <el-card v-else shadow="hover" class="stream-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Promotion /></el-icon>
                AI旅行推荐方案
              </span>
              <el-tag type="success" size="small">实时生成</el-tag>
            </div>
          </template>

          <div class="card-content">
            <el-descriptions border :column="1" size="large" class="content-desc">
              <el-descriptions-item label="推荐详情">
                <div class="stream-content" v-html="parseStreamContent"/>
              </el-descriptions-item>
            </el-descriptions>

            <div class="card-footer">
              <el-button text size="small"
                  :class="{ collected: isCollected }"
                  :disabled="!streamCompleted || countDown > 0"
                  @click="handleCollect"
              >
                <el-icon>
                  <StarFilled v-if="isCollected" />
                  <Star v-else />
                </el-icon>

                <span>
                  {{countDown > 0 ? `收藏方案(${countDown}s)` : "收藏方案" }}
                </span>
              </el-button>

              <span class="create-time">
                生成时间：{{ formatDate(new Date()) }}
              </span>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script>
import axios from "axios";
import { ElMessage } from "element-plus";

import {
  Flag,
  Promotion,
  Refresh,
  Setting,
  Star,
  StarFilled,
  User,
} from "@element-plus/icons-vue";

export default {
  name: "TravelRecommendation",
  components: {
    Flag,
    Promotion,
    Refresh,
    Setting,
    Star,
    StarFilled,
    User,
  },
  data() {
    return {
      streamResult: "",
      eventSource: null,
      loading: false,
      isCollected: false,
      submitLoading: false,
      streamCompleted: true,
      countDown: 0,
      countDownTimer: null,
      userPreferences: {
        userId: null,
        departureCity: "浙江杭州",
        destinationType: "海滨度假",
        budgetRange: "舒适型",
        travelTime: "寒假",
        companionType: "独自旅行",
        activityPref: "",
        activityPrefArray: [],
        accommodationPref: "",
        transportation: "",
        specialRequire: "",
        createdAt: null,
      },
      recommendations: [],
      formRules: {
        departureCity: [
          {
            required: true,
            message: "请输入出发地",
            trigger: "blur",
          },
        ],
        destinationType: [
          {
            required: true,
            message: "请选择目的地类型",
            trigger: "change",
          },
        ],
        budgetRange: [
          {
            required: true,
            message: "请选择预算范围",
            trigger: "change",
          },
        ],
        travelTime: [
          {
            required: true,
            message: "请输入出行时间",
            trigger: "blur",
          },
        ],
        companionType: [
          {
            required: true,
            message: "请选择同行人类型",
            trigger: "change",
          },
        ],
      },
      user: "",
    };
  },

  computed: {
    // 生成用户选项预览
    userMessagePreview() {
      const requiredFields = [
        "departureCity",
        "destinationType",
        "budgetRange",
        "travelTime",
        "companionType",
      ];
      if (requiredFields.some((field) => !this.userPreferences[field])) return "";

      let message = "我想规划一次旅行，";
      message += `我从${this.userPreferences.departureCity}出发，`;
      message += `我喜欢的目的地类型是${this.userPreferences.destinationType}，`;
      message += `我的预算范围是${this.userPreferences.budgetRange}，`;
      message += `计划${this.userPreferences.travelTime}出行，`;
      message += `同行人是${this.userPreferences.companionType}。`;
      if (this.userPreferences.activityPref) message += `我喜欢${this.userPreferences.activityPref}，`;
      if (this.userPreferences.accommodationPref) message += `住宿方面偏好${this.userPreferences.accommodationPref}，`;
      if (this.userPreferences.transportation) message += `交通方式希望主要使用${this.userPreferences.transportation}，`;
      if (this.userPreferences.specialRequire) message += `另外我还有一些特殊要求：${this.userPreferences.specialRequire}。`;
      message += "请根据以上信息，为我推荐合适的目的地，并给出推荐理由、预估费用、最佳出行季节、住宿建议、交通建议和特别提示。";
      return message;
    },
    // 将 stream 数据转换为 html
    parseStreamContent() {
      let content = this.streamResult;
      content = content.replace(/#/g, "");
      content = content.replace(/---/g, "");
      content = content.replace(/-/g, "");
      content = content.replace(/>/g, "");
      content = content.replace(/\|/g, "");
      content = content.replace(/\*\*(.+?)\*\*/g, "<b>$1</b>");
      content = content.replace(/\n/g, "<br/>");
      return content;
    },
  },

  methods: {
    // 偏好设置更新
    updateActivityPref(selected) {
      this.userPreferences.activityPref = selected.join("、");
    },
    // 获取AI推荐
    async submitPreferences() {
      const form = this.$refs.preferenceForm;
      if (!form) return;
      /*
       * Vue3 + Element Plus
       * 等待表单校验完成。
       * 校验不通过时不会继续调用后端。
       */
      const valid = await form.validate().catch(() => false);
      if (!valid) return;
      // 防止之前的 SSE 连接没有关闭
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
      }
      // 重置之前的倒计时
      this.resetCountDown();
      this.submitLoading = true;
      this.loading = true;
      this.streamResult = "";
      this.streamCompleted = false;
      this.isCollected = false;
      const requestUrl = "http://localhost:8181/suggest?msg=" + encodeURIComponent(this.userMessagePreview);
      /*
       * 建立 SSE 连接
       */
      this.eventSource = new EventSource(requestUrl);
      /*
       * 接收服务端推送的数据
       */
      this.eventSource.onmessage = (event) => {
        this.loading = false;
        this.streamResult += event.data;
        this.scrollToBottom();
      };
      /*
       * SSE 连接结束或发生错误
       */
      this.eventSource.onerror = () => {
        this.stopStream();
        this.streamCompleted = true;
        // 流式生成结束后开启20秒倒计时
        this.startCountDown();
      };
    },
    // 浏览器滚动条自适应
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.resultScrollContainer;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
    },
    // 关闭 SSE 流
    stopStream() {
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
      }
      this.submitLoading = false;
      this.loading = false;
      this.streamCompleted = true;
    },
    // 重置表单
    resetForm() {
      const form = this.$refs.preferenceForm;
      if (form) form.resetFields();
      this.userPreferences.specialRequire = "";
      this.userPreferences.activityPrefArray = [];
      this.userPreferences.activityPref = "";
      this.streamResult = "";
      this.recommendations = [];
      this.streamCompleted = true;
      this.isCollected = false;
      // 关闭现有 SSE
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
      }
      // 重置倒计时
      this.resetCountDown();
    },
    // 日期格式化
    formatDate(timestamp) {
      if (!timestamp) return "N/A";
      const date = new Date(timestamp);
      if (isNaN(date.getTime())) return "无效日期";
      return date.toLocaleString(
          "zh-CN",
          {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
          }
      );
    },
    // 收藏方法
    handleCollect() {
      const userString = localStorage.getItem("user");
      if (!userString) {
        ElMessage.warning("请先登录后再收藏！");
        return;
      }
      this.user = JSON.parse(userString);
      if (!this.user?.id) {
        ElMessage.warning("用户信息异常，请重新登录！");
        return;
      }
      if (this.isCollected) {
        return;
      }
      axios.post("http://localhost:8181/collect",{
        userId: this.user.id,
        content: this.streamResult,
      }).then((response) => {
            if (response.data) {
              this.isCollected = true;
              ElMessage.success("收藏成功！");
            } else {
              ElMessage.error("收藏失败！");
            }
          }).catch(() => {
            ElMessage.error("收藏请求失败！");
          });
    },
    // 启动20秒倒计时
    startCountDown() {
      this.countDown = 20;
      /*
       * 防止重复创建定时器
       */
      if (this.countDownTimer) clearInterval(this.countDownTimer);
      this.countDownTimer =
          setInterval(() => {
            if (this.countDown > 0) {
              this.countDown--;
            } else {
              clearInterval(this.countDownTimer);
              this.countDownTimer = null;
            }
          }, 1000);
    },
    // 重置倒计时
    resetCountDown() {
      this.countDown = 0;
      if (this.countDownTimer) {
        clearInterval(this.countDownTimer);
        this.countDownTimer = null;
      }
    },
  },
  /*
   * Vue 3 生命周期
   * Vue 2 的 beforeDestroy
   * 改为 beforeUnmount
   */
  beforeUnmount() {
    this.stopStream();
    this.resetCountDown();
  },
};
</script>

<style scoped>
.stream-content b {
  font-weight: 700;
  color: #303133;
}

.stream-content {
  font-size: 15px;
  color: #333333;
  line-height: 1.8;
  text-align: left !important;
  direction: ltr !important;
  word-wrap: break-word;
  width: 100%;
  padding: 8px 0;
}

.travel-recommendation-app {
  min-height: 100vh;
  background-color: #f5f7fa;

  font-family:
      "Helvetica Neue",
      Arial,
      "PingFang SC",
      "Microsoft YaHei",
      sans-serif;

  padding-top: 84px;
  margin-top: -100px;
}

.main-container {
  display: flex;

  max-width: 1600px;

  margin: 0 auto;

  padding: 24px;

  gap: 24px;
}

.form-container {
  flex: 1;

  min-width: 500px;

  background-color: white;

  border-radius: 8px;

  box-shadow:
      0 2px 12px 0
      rgba(0, 0, 0, 0.1);

  padding: 24px;

  overflow-y: auto;

  max-height: 600px;
}

.result-container {
  flex: 1.2;

  background-color: white;

  border-radius: 8px;

  box-shadow:
      0 2px 12px 0
      rgba(0, 0, 0, 0.1);

  padding: 24px;

  overflow-y: auto;

  max-height: 600px;
}

.form-section {
  margin-bottom: 32px;

  padding-bottom: 24px;

  border-bottom:
      1px solid #e6e9f0;
}

.form-section:last-child {
  border-bottom: none;
}

.section-title {
  font-size: 18px;

  color: #409eff;

  margin-bottom: 20px;

  display: flex;

  align-items: center;

  gap: 8px;
}

.form-actions {
  margin-top: 32px;

  padding-top: 24px;

  text-align: center;
}

.form-tips {
  margin-top: 20px;

  padding: 16px;

  background-color: #f0f9ff;

  border-radius: 6px;

  border-left:
      4px solid #409eff;

  text-align: left !important;
}

.preview-content {
  margin-top: 8px;

  padding: 12px;

  background-color: white;

  border-radius: 4px;

  border:
      1px solid #dcdfe6;

  font-size: 14px;

  line-height: 1.5;

  color: #606266;

  max-height: 200px;

  overflow-y: auto;

  text-align: left !important;

  white-space: pre-wrap;
}

.empty-result {
  text-align: center;

  padding:
      60px 20px;

  color: #909399;
}

.empty-icon {
  font-size: 60px;

  color: #c0c4cc;

  margin-bottom: 20px;
}

.empty-result h3 {
  font-size: 20px;

  margin-bottom: 12px;

  color: #606266;
}

.stream-card {
  width: 100%;

  border-radius:
      8px !important;

  overflow: hidden;
}

.card-header {
  display: flex;

  justify-content:
      space-between;

  align-items: center;

  font-weight: 600;
}

.card-title {
  font-size: 16px;

  color: #303133;

  display: flex;

  align-items: center;

  gap: 6px;
}

.card-content {
  width: 100%;
}

.content-desc {
  --el-descriptions-item-label-color:
      #409eff;

  --el-descriptions-item-label-font-weight:
      600;
}

.card-footer {
  display: flex;

  justify-content:
      space-between;

  align-items: center;

  margin-top: 20px;

  padding-top: 12px;

  border-top:
      1px solid #f0f0f0;

  font-size: 12px;

  color: #909399;
}

.create-time {
  flex: 1;

  text-align: right;
}

:deep(.collected) {
  color:
      #f7ba1e !important;
}

:deep(.el-button.is-disabled) {
  color:
      #c0c4cc !important;

  cursor:
      not-allowed;
}

@media (max-width: 1200px) {
  .main-container {
    flex-direction:
        column;
  }

  .form-container,
  .result-container {
    min-width: auto;

    max-height: none;
  }
}

@media (max-width: 768px) {
  .main-container {
    padding: 16px;
  }

  .form-container,
  .result-container {
    padding: 16px;
  }
}
</style>