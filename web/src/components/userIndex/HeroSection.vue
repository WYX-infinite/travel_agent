<template>
  <section class="hero" :style="heroBackgroundStyle">
    <div class="hero-overlay"></div>
    <div class="container hero-content">
      <div class="welcome-section" v-if="user">
        <h2 class="hero-title">欢迎回来，{{ user.name }}</h2>
        <p class="hero-subtitle">为你精选了更多优质路线，开启新的旅程吧！</p>
      </div>

      <div class="search-form-wrapper">
        <div class="search-form-container">
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">
                <el-icon><Location /></el-icon>
                出发地
              </label>
              <div class="departure-selector">
                <el-cascader
                  v-model="selectedDeparture"
                  :options="provinceCityOptions"
                  :props="cascaderProps"
                  placeholder="请先选择省份，再选择城市"
                  class="departure-cascader"
                  clearable
                  filterable
                  @change="handleDepartureChange"
                >
                </el-cascader>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">
                <el-icon><Place /></el-icon>
                目的地
              </label>
              <el-input
                ref="destinationInput"
                v-model="destination"
                placeholder="输入城市名称/景点"
                class="form-input"
                @keyup.enter="handleSearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>

            <div class="form-group button-group">
              <el-button
                type="primary"
                class="search-button"
                @click="handleSearch"
                :loading="searchLoading"
                :disabled="!isSearchValid"
              >
                <el-icon><Search /></el-icon>
                {{ searchLoading ? '搜索中...' : '搜索线路' }}
              </el-button>
            </div>
          </div>

          <div class="quick-tips">
            <span class="tips-label">热门目的地：</span>
            <el-tag
              v-for="tip in quickTips"
              :key="tip"
              class="tip-tag"
              @click="selectTip(tip)"
            >
              {{ tip }}
            </el-tag>
          </div>

          <div
            class="recent-departures"
            v-if="recentDepartures.length > 0"
          >
            <span class="tips-label">最近选择：</span>
            <el-tag
              v-for="departure in recentDepartures"
              :key="departure.value"
              class="recent-tag"
              type="info"
              @click="selectRecentDeparture(departure)"
              closable
              @close="removeRecentDeparture(departure.value)"
            >
              <el-icon><Location /></el-icon>
              {{ departure.label }}
            </el-tag>
          </div>

          <div class="search-stats">
            <span class="stats-text">
              <el-icon><DataAnalysis /></el-icon>
              {{ searchStatsText }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { DataAnalysis, Location, Place, Search } from "@element-plus/icons-vue";
import userIndexImg from "@/assets/userIndex/background.png";
import { regionData } from "element-china-area-data";

export default {
  name: "HeroSection",
  components: {
    DataAnalysis,
    Location,
    Place,
    Search,
  },
  emits: ["departure-change"],
  props: {
    quickTips: {
      type: Array,
      default: () => [
        "八达岭长城",
        "上海外滩",
        "深圳湾公园",
        "洪崖洞",
        "大雁塔",
        "澳门银河",
      ],
    },
    searchStatsText: {
      type: String,
      default: "推荐出发地：珠海、广州、上海等共收录3348条市级线路",
    },
  },
  data() {
    return {
      user: null,
      selectedDeparture: [],
      destination: "",
      provinceCityOptions: [],
      cascaderProps: {
        value: "value",
        label: "label",
        children: "children",
        checkStrictly: false,
        emitPath: true,
        expandTrigger: "click",
      },
      recentDepartures: [],
      searchLoading: false,
      showSuggestions: false,
      allCities: [],
      destinationSuggestions: [],
    };
  },
  computed: {
    heroBackgroundStyle() {
      return {
        backgroundImage: `url(${userIndexImg})`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
      };
    },
    isSearchValid() {
      const departureValid =
        this.selectedDeparture && this.selectedDeparture.length > 0;
      const destinationValid =
        this.destination && this.destination.trim().length > 0;
      return departureValid && destinationValid;
    },
  },
  watch: {
    destination(newVal) {
      if (newVal && newVal.trim().length >= 1) {
        this.filterDestinationSuggestions(newVal.trim());
        this.showSuggestions = true;
      } else {
        this.destinationSuggestions = [];
        this.showSuggestions = false;
      }
    },
  },
  mounted() {
    this.initUserData();
    this.initProvinceCityData();
    this.loadRecentDepartures();
    document.addEventListener("click", this.handleClickOutside);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.handleClickOutside);
  },
  methods: {
    initUserData() {
      const savedUser = localStorage.getItem("user");
      if (savedUser) {
        try {
          this.user = JSON.parse(savedUser);
          if (typeof this.user !== "object" || !this.user.name) {
            throw new Error("用户数据格式不正确");
          }
        } catch (error) {
          console.error("解析用户数据失败:", error);
          this.user = { name: "旅行者" };
        }
      }
    },

    initProvinceCityData() {
      const allCities = [];
      this.provinceCityOptions = regionData.map((province) => {
        if (["北京市", "上海市", "天津市", "重庆市"].includes(province.label)) {
          const cityName = province.label.replace("市", "");
          allCities.push(cityName);
          return {
            value: province.value,
            label: province.label.replace("市", ""),
            children: [
              {
                value: province.value,
                label: cityName,
              },
            ],
          };
        }

        const children = [];
        if (province.children) {
          province.children.forEach((city) => {
            if (city.children && city.children.length > 0) {
              const cityName = city.label
                .replace("市", "")
                .replace("地区", "")
                .replace("自治州", "");
              allCities.push(cityName);
              children.push({
                value: city.value,
                label: cityName,
              });
            }
          });
        }

        children.sort((a, b) => a.label.localeCompare(b.label, "zh-CN"));

        return {
          value: province.value,
          label: province.label,
          children: children,
        };
      });

      const hotProvinces = [
        "北京市",
        "上海市",
        "广东省",
        "陕西省",
        "四川省",
        "浙江省",
        "重庆市",
        "江苏省",
        "福建省",
      ];

      this.provinceCityOptions.sort((a, b) => {
        const aIndex = hotProvinces.indexOf(a.label);
        const bIndex = hotProvinces.indexOf(b.label);
        if (aIndex !== -1 && bIndex !== -1) return aIndex - bIndex;
        if (aIndex !== -1) return -1;
        if (bIndex !== -1) return 1;
        return a.label.localeCompare(b.label, "zh-CN");
      });

      this.allCities = allCities.sort((a, b) => a.localeCompare(b, "zh-CN"));
    },

    handleDepartureChange(value) {
      // 已修改: 简化逻辑，只负责保存到最近选择和触发事件
      if (value && value.length === 2) {
        const [provinceCode, cityCode] = value;
        const province = this.provinceCityOptions.find(
          (p) => p.value === provinceCode
        );
        if (province) {
          const city = province.children.find((c) => c.value === cityCode);
          if (city) {
            this.saveToRecentDepartures(city.label, city.value);
            // 新增: 触发事件，通知父组件出发地已改变
            this.$emit("departure-change", {
              label: city.label,
              value: city.value,
            });
          }
        }
      }
    },

    // 已移除: updateRelatedContent 方法

    filterDestinationSuggestions(input) {
      const inputLower = input.toLowerCase();
      this.destinationSuggestions = this.allCities
        .filter(
          (city) => city.toLowerCase().includes(inputLower)
          // 已移除: 不再需要排除 selectedDepartureCity
        )
        .slice(0, 8);
    },

    selectDestination(city) {
      this.destination = city;
      this.showSuggestions = false;
      this.$nextTick(() => {
        this.$refs.destinationInput?.focus();
      });
    },

    // 已移除: selectAttraction 方法

    handleClickOutside(event) {
      const inputElement = this.$refs.destinationInput?.$el;
      if (inputElement && !inputElement.contains(event.target)) {
        this.showSuggestions = false;
      }
    },

    loadRecentDepartures() {
      const saved = localStorage.getItem("recentDepartures");
      if (saved) {
        try {
          this.recentDepartures = JSON.parse(saved);
        } catch (error) {
          console.error("加载最近出发地失败:", error);
          this.recentDepartures = [];
        }
      }
    },

    saveToRecentDepartures(label, value) {
      const newDeparture = { label, value, timestamp: Date.now() };
      this.recentDepartures = this.recentDepartures.filter(
        (d) => d.value !== value
      );
      this.recentDepartures.unshift(newDeparture);
      if (this.recentDepartures.length > 5) {
        this.recentDepartures.pop();
      }
      localStorage.setItem(
        "recentDepartures",
        JSON.stringify(this.recentDepartures)
      );
    },

    selectRecentDeparture(departure) {
      let foundPath = null;
      for (const province of this.provinceCityOptions) {
        const city = province.children.find((c) => c.value === departure.value);
        if (city) {
          foundPath = [province.value, city.value];
          break;
        }
      }

      if (foundPath) {
        this.selectedDeparture = foundPath;
      }
    },

    removeRecentDeparture(value) {
      this.recentDepartures = this.recentDepartures.filter(
        (d) => d.value !== value
      );
      localStorage.setItem(
        "recentDepartures",
        JSON.stringify(this.recentDepartures)
      );
    },

    selectTip(tip) {
      this.destination = tip;
      this.showSuggestions = false;
      this.$nextTick(() => {
        this.$refs.destinationInput?.focus();
      });
    },

    handleSearch() {
      if (!this.isSearchValid) {
        this.$message.warning("请选择出发地并输入目的地！");
        return;
      }
      this.searchLoading = true;
      try {
        let departureCity = "";
        if (this.selectedDeparture && this.selectedDeparture.length === 2) {
          const [provinceCode, cityCode] = this.selectedDeparture;
          const province = this.provinceCityOptions.find(
            (p) => p.value === provinceCode
          );
          if (province) {
            const city = province.children.find((c) => c.value === cityCode);
            if (city) {
              departureCity = city.label;
            }
          }
        }
        const destinationCity = this.destination.trim();
        console.log(
          "搜索参数 - 出发地:",
          departureCity,
          "目的地:",
          destinationCity
        );
        const baseUrl = "https://dujia.qunar.com/pdqk/list";
        const url = `${baseUrl}_${encodeURIComponent(
          departureCity
        )}_${encodeURIComponent(
          destinationCity
        )}_all?ti=3&tm=l01_all_search_origin`;
        console.log("生成的市级线路搜索URL:", url);
        this.$nextTick(() => {
          window.open(url, "_blank");
          this.searchLoading = false;
        });
      } catch (error) {
        console.error("搜索过程中出现错误:", error);
        this.$message.error("搜索过程中出现错误，请重试！");
        this.searchLoading = false;
      }
    },
  },
};
</script>

<style scoped>
.hero {
  color: white;
  padding: 80px 0 130px; /* 增加底部内边距 */
  position: relative;
  margin-bottom: 100px; /* 增加底部外边距 */
  width: 100%;
}

.hero-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to right, rgba(0, 0, 0, 0.7), rgba(0, 0, 0, 0.3));
}

.hero-content {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px 30px;
}

.welcome-section {
  margin-bottom: 40px;
  max-width: 800px;
}

.hero-title {
  font-size: 2.8rem;
  font-weight: 700;
  margin-bottom: 10px;
  text-align: left;
}

.hero-subtitle {
  font-size: 1.2rem;
  opacity: 0.9;
  margin-bottom: 20px;
  text-align: left;
}

.search-form-wrapper {
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
}

.search-form-container {
  background: white;
  border-radius: 16px;
  padding: 30px 40px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.1), 0 6px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 20px;
  align-items: start;
  margin-bottom: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
  min-height: 80px;
}

.form-label {
  font-size: 0.95rem;
  font-weight: 500;
  color: #2c3e50;
  display: flex;
  align-items: center;
  gap: 6px;
}

.form-label i {
  color: #409eff;
  font-size: 16px;
}

.departure-selector {
  position: relative;
}

.departure-cascader {
  width: 100%;
}

.departure-cascader :deep(.el-cascader) {
  width: 100%;
}

.departure-cascader :deep(.el-input__wrapper) {
  height: 52px;
  border-radius: 10px;
  padding: 0 15px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  border: 1px solid #e0e6ed;
  transition: all 0.25s ease;
}

.departure-cascader :deep(.el-input__wrapper:hover) {
  border-color: #c0c4cc;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.departure-cascader :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2), 0 2px 8px rgba(0, 0, 0, 0.08);
  border-color: #409eff;
}

/* 已移除: .selected-location 及其子元素样式 */
/* 已移除: .related-content-wrapper 及其子元素样式 */

.region-count {
  font-size: 0.8rem;
  color: #909399;
  margin-left: 4px;
}

.form-input {
  --el-input-height: 52px;
  --el-input-border-radius: 10px;
  --el-input-text-color: #333;
  --el-input-bg-color: #fff;
  --el-input-border-color: #e0e6ed;
  --el-input-hover-border-color: #409eff;
  --el-input-focus-border-color: #409eff;
}

.form-input :deep(.el-input__wrapper) {
  padding: 0 15px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  border: 1px solid #e0e6ed;
  transition: all 0.25s ease;
}

.form-input :deep(.el-input__wrapper:hover) {
  border-color: #c0c4cc;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.form-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2), 0 2px 8px rgba(0, 0, 0, 0.08);
  border-color: #409eff;
}

.form-input :deep(.el-input__prefix) {
  display: flex;
  align-items: center;
  margin-right: 8px;
}

.form-input :deep(.el-input__prefix i) {
  color: #409eff;
  font-size: 18px;
}

.autocomplete-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  z-index: 1000;
  max-height: 200px;
  overflow-y: auto;
  margin-top: 4px;
}

.suggestion-item {
  padding: 10px 15px;
  cursor: pointer;
  transition: background-color 0.2s;
  font-size: 14px;
  color: #606266;
}

.suggestion-item:hover {
  background-color: #f5f7fa;
}

.button-group {
  display: flex;
  height: 52px;
  align-items: flex-end;
}

.search-button {
  --el-button-bg-color: #409eff;
  --el-button-border-color: #409eff;
  --el-button-hover-bg-color: #337ecc;
  --el-button-hover-border-color: #337ecc;
  --el-button-active-bg-color: #2b6cb0;
  --el-button-active-border-color: #2b6cb0;

  margin-top: 25px;
  height: 50px;
  min-width: 120px;
  padding: 0 30px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 10px;
  transition: all 0.25s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.search-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(64, 158, 255, 0.3);
}

.search-button:active:not(:disabled) {
  transform: translateY(0);
}

.search-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.search-button i {
  font-size: 18px;
}

.quick-tips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 15px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  margin-bottom: 15px;
}

.tips-label {
  font-size: 0.9rem;
  color: #666;
  white-space: nowrap;
}

.tip-tag {
  --el-tag-bg-color: rgba(64, 158, 255, 0.08);
  --el-tag-border-color: rgba(64, 158, 255, 0.2);
  --el-tag-text-color: #409eff;
  font-size: 0.85rem;
  height: 28px;
  line-height: 28px;
  padding: 0 12px;
  cursor: pointer;
}

.tip-tag:hover {
  background-color: rgba(64, 158, 255, 0.15);
  transform: translateY(-1px);
}

.recent-departures {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0;
  margin-bottom: 15px;
}

.recent-tag {
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 0.85rem;
  height: 28px;
  line-height: 28px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.recent-tag:hover {
  background-color: rgba(144, 147, 153, 0.1);
  transform: translateY(-1px);
}

.recent-tag i {
  font-size: 12px;
}

.search-stats {
  padding-top: 10px;
  border-top: 1px dashed rgba(0, 0, 0, 0.1);
}

.stats-text {
  font-size: 0.85rem;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 6px;
}

.stats-text i {
  font-size: 14px;
}

@media (max-width: 768px) {
  .hero {
    padding: 60px 0;
  }
  .hero-title {
    font-size: 2rem;
  }
  .form-grid {
    grid-template-columns: 1fr;
    gap: 15px;
  }
  .search-form-container {
    padding: 25px 20px;
    border-radius: 14px;
  }
  .form-group {
    min-height: auto;
  }
  .button-group {
    height: auto;
    align-items: flex-start;
  }
  .search-button {
    min-width: 100%;
    height: 48px;
  }
  .quick-tips,
  .recent-departures {
    justify-content: flex-start;
  }
  .tips-label {
    width: 100%;
    margin-bottom: 5px;
  }
}

@media (max-width: 480px) {
  .quick-tips {
    justify-content: flex-start;
  }
  .tips-label {
    width: 100%;
    margin-bottom: 5px;
  }
}
</style>
