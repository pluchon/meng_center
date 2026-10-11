<template>
  <el-dialog
    :model-value="Boolean(target)"
    :title="t('detail.reportTitle')"
    width="min(480px,calc(100% - 32px))"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @close="$emit('close')"
  >
    <div class="report-reasons">
      <button
        v-for="value in ReportReasons"
        :key="value"
        type="button"
        :class="{selected:reason===value}"
        :aria-pressed="reason===value"
        :disabled="busy"
        @click="reason=value"
      >
        {{ t('detail.reasons.'+value) }}
      </button>
    </div>
    <el-input
      v-model="note"
      maxlength="200"
      show-word-limit
      :placeholder="t('detail.reportNote')"
      :disabled="busy"
    />
    <template #footer>
      <el-button
        :disabled="busy"
        @click="$emit('close')"
      >
        {{ t('detail.cancel') }}
      </el-button><el-button
        type="primary"
        :disabled="!reason"
        :loading="busy"
        @click="submit"
      >
        {{ t('detail.reportSubmit') }}
      </el-button>
    </template>
  </el-dialog>
</template>
<script src="./ReportDialog.js"></script>
<style lang="scss" scoped src="./ReportDialog.scss"></style>
