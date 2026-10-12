import { init, use } from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
use([LineChart, BarChart, GridComponent, TooltipComponent, SVGRenderer])
export { init }
