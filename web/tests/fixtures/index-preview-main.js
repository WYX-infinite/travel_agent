import { createApp } from "vue";
import Index from "../../src/views/Index.vue";
import axiosPlugin from "../../src/plugins/axios";
import elementPlugin from "../../src/plugins/element";

const app = createApp(Index);

app.use(axiosPlugin);
app.use(elementPlugin);
app.config.globalProperties.$router = {
  push() {},
};
app.mount("#app");
