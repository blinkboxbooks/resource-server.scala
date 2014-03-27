#ugly html formatter override by @aliaksandr

require 'cucumber/formatter/html'

module Cucumber
  module Formatter

    class HtmlCustomFormatter < Cucumber::Formatter::Html

      def timestamp
        ts = Time.now #- TEST_RUN_START
        formatted_timestamp = ts.strftime("%b %e %H:%M:%S")
        formatted_timestamp
      end

      def append_timestamp_to( name )
        "#{name} [#{timestamp}]"
      end

      def prefix_with_timestamp( name )
        "#{timestamp} #{name}"
      end

      #override inline JS in the default html formatter to always show cucumber tags regardless expand/collapse state of the scenarios
      #also make all scenarios collapsed on page load
      def inline_js_content
        <<-EOF

        SCENARIOS = "h3[id^='scenario_'],h3[id^=background_]";

        $(document).ready(function() {
          $(SCENARIOS).css('cursor', 'pointer');
          $(SCENARIOS).click(function() {
            $(this).siblings().toggle(250, function(){
            $(this).siblings(".tag").show();});
          });

          $("#collapser").css('cursor', 'pointer');
          $("#collapser").click(function() {
            $(SCENARIOS).siblings().hide();
            $(SCENARIOS).siblings(".tag").show();
          });

          $("#expander").css('cursor', 'pointer');
          $("#expander").click(function() {
            $(SCENARIOS).siblings().show();
          });

          $(SCENARIOS).siblings().hide();
          $(SCENARIOS).siblings(".tag").show();
        })

        function moveProgressBar(percentDone) {
          $("cucumber-header").css('width', percentDone +"%");
        }
        function makeRed(element_id) {
          $('#'+element_id).css('background', '#C40D0D');
          $('#'+element_id).css('color', '#FFFFFF');
        }
        function makeYellow(element_id) {
          $('#'+element_id).css('background', '#FAF834');
          $('#'+element_id).css('color', '#000000');
        }

        EOF
      end

      #log scenario name so that console log is more readable
      #also track the position in the all logs array from which out for current scenario starts
      def scenario_name(keyword, name, file_colon_line, source_indent)
        name = append_timestamp_to(name)
        super(keyword, name, file_colon_line, source_indent)
      end

      def step_name(keyword, step_match, status, source_indent, background, file_colon_line)
        name = super(keyword, step_match, status, source_indent, background, file_colon_line)
        append_timestamp_to(name)
      end

      def build_step(keyword, step_match, status)
        super(keyword, step_match, status)

        @builder.div(:class => 'step_file') do |div|
          @builder.span do
            @builder << append_timestamp_to("")
          end
        end
      end

      def after_table_row(table_row)
        return if @hide_this_step
        print_table_row_messages
        @builder << '</tr>'
        if table_row.exception
          @builder.tr do
            @builder.td(:colspan => @col_index.to_s, :class => 'failed') do
              @builder.pre do |pre|
                pre << format_exception(table_row.exception)
              end
            end
          end
          save_screenshot_with_filename_based_on(table_row)
          set_scenario_color_failed
        end
        if @outline_row
          @outline_row += 1
        end
        @step_number += 1
        move_progress
      end

      #override to set color of pending exceptions backtraces to Yellow instead of Red (and thus not make the whole scenario Red)
      def after_table_row(table_row)
        return if @hide_this_step
        print_table_row_messages
        @builder << '</tr>'
        if table_row.exception
          if table_row.exception.instance_of?(Cucumber::Pending)
            @builder.tr do
              @builder.td(:colspan => @col_index.to_s, :class => 'pending') do
                @builder.pre do |pre|
                  pre << h(format_exception(table_row.exception))
                end
              end
            end
            set_scenario_color_pending
          else
            @builder.tr do
              @builder.td(:colspan => @col_index.to_s, :class => 'failed') do
                @builder.pre do |pre|
                  pre << h(format_exception(table_row.exception))
                end
              end
            end
            set_scenario_color_failed
          end
        end
        if @outline_row
          @outline_row += 1
        end
        @step_number += 1
        move_progress
      end


    end

  end


end